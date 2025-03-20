package ru.mirea.prometheus.exporter;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusScrapeRequest;
import io.prometheus.metrics.model.snapshots.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import ru.mirea.prometheus.exporter.RemoteWriteRequest.TimeSeries;
import ru.mirea.prometheus.exporter.RemoteWriteRequest.Label;
import ru.mirea.prometheus.exporter.RemoteWriteRequest.Sample;
import ru.mirea.prometheus.exporter.RemoteWriteRequest.WriteRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class MetricExporter implements AutoCloseable {

    private static final int MAX_SAMPLES_PER_SEND = 1000;
    private static final int DEFAULT_SEND_INTERVAL_SECONDS = 60;

    private final SystemInfo systemInfo;
    private final int sendIntervalSeconds;
    private final PrometheusRemoteWriteClient remoteWriteClient;
    private final PrometheusMeterRegistry meterRegistry;

    private final ScheduledExecutorService executorService;
    private final Map<Class<? extends MetricSnapshot>, TriFunction<MetricSnapshot, String, Long, List<TimeSeries>>> processors;

    public MetricExporter(SystemInfo systemInfo,
                          PrometheusMeterRegistry meterRegistry,
                          PrometheusRemoteWriteClient remoteWriteClient) {
        this(systemInfo, DEFAULT_SEND_INTERVAL_SECONDS, meterRegistry, remoteWriteClient);
    }

    public MetricExporter(SystemInfo systemInfo, int sendIntervalSeconds,
                          PrometheusMeterRegistry meterRegistry,
                          PrometheusRemoteWriteClient remoteWriteClient) {
        if (sendIntervalSeconds <= 0) {
            throw new IllegalArgumentException("SendInterval must be greater than 0");
        }
        this.systemInfo = systemInfo;
        this.meterRegistry = meterRegistry;
        this.remoteWriteClient = remoteWriteClient;
        this.sendIntervalSeconds = sendIntervalSeconds;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.processors = new HashMap<>();
        initializeProcessors();
    }

    private void initializeProcessors() {
        processors.put(CounterSnapshot.class, this::processCounterSnapshot);
        processors.put(GaugeSnapshot.class, this::processGaugeSnapshot);
        processors.put(SummarySnapshot.class, this::processSummarySnapshot);
        processors.put(HistogramSnapshot.class, this::processHistogramSnapshot);
        processors.put(InfoSnapshot.class, this::processInfoSnapshot);
        processors.put(StateSetSnapshot.class, this::processStateSetSnapshot);
        processors.put(UnknownSnapshot.class, this::processUnknownSnapshot);
    }

    public void startSendingMetrics() {
        executorService.scheduleAtFixedRate(() ->
                CompletableFuture.runAsync(this::sendMetrics)
                        .exceptionally(e -> {
                            log.error("Send Metrics Job: an exception occurred trying to send metrics", e);
                            return null;
                        }),
                0, sendIntervalSeconds, TimeUnit.SECONDS);
    }

    private void sendMetrics() {
        List<TimeSeries> metrics = collectMetrics();
        WriteRequest.Builder writeRequest = WriteRequest.newBuilder();

        for (TimeSeries timeSeries : metrics) {
            writeRequest.addTimeseries(timeSeries);
            if (writeRequest.getTimeseriesCount() == MAX_SAMPLES_PER_SEND) {
                remoteWriteClient.write(writeRequest.build());
                writeRequest.clear();
            }
        }

        if (writeRequest.getTimeseriesCount() > 0) {
            remoteWriteClient.write(writeRequest.build());
        }
    }

    private List<TimeSeries> collectMetrics() {
        MetricSnapshots snapshots = meterRegistry
                .getPrometheusRegistry()
                .scrape((PrometheusScrapeRequest) null);

        long timestamp = OffsetDateTime.now().toInstant().toEpochMilli();

        List<TimeSeries> metrics = new ArrayList<>();
        for (MetricSnapshot snapshot : snapshots) {
            String meterName = snapshot.getMetadata().getPrometheusName();
            TriFunction<MetricSnapshot, String, Long, List<TimeSeries>> processor = processors.get(snapshot.getClass());
            if (processor != null) {
                metrics.addAll(processor.apply(snapshot, meterName, timestamp));
            }
        }
        return metrics;
    }

    private List<TimeSeries> processCounterSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        CounterSnapshot counterSnapshot = (CounterSnapshot) snapshot;
        return counterSnapshot.getDataPoints().stream()
                .map(snap -> buildTimeSeries(meterName, snap.getLabels(), snap.getValue(), timestamp))
                .toList();
    }

    private List<TimeSeries> processGaugeSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
        return gaugeSnapshot.getDataPoints().stream()
                .map(snap -> buildTimeSeries(meterName, snap.getLabels(), snap.getValue(), timestamp))
                .toList();
    }

    private List<TimeSeries> processSummarySnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        SummarySnapshot summarySnapshot = (SummarySnapshot) snapshot;
        List<TimeSeries> metrics = new ArrayList<>();
        summarySnapshot.getDataPoints().forEach(snap -> {
            if (snap.hasSum()) {
                metrics.add(buildTimeSeries(meterName + "_sum", snap.getLabels(), snap.getSum(), timestamp));
            }
            if (snap.hasCount()) {
                metrics.add(buildTimeSeries(meterName + "_count", snap.getLabels(), snap.getCount(), timestamp));
            }
            snap.getQuantiles().forEach(snapQuantile -> {
                Labels labels = snap.getLabels().add("quantile", String.valueOf(snapQuantile.getQuantile()));
                metrics.add(buildTimeSeries(meterName, labels, snapQuantile.getQuantile(), timestamp));
            });
        });
        return metrics;
    }

    private List<TimeSeries> processHistogramSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        HistogramSnapshot histogramSnapshot = (HistogramSnapshot) snapshot;
        List<TimeSeries> metrics = new ArrayList<>();
        histogramSnapshot.getDataPoints().forEach(snap -> {
            if (snap.hasSum()) {
                metrics.add(buildTimeSeries(meterName + "_sum", snap.getLabels(), snap.getSum(), timestamp));
            }
            if (snap.hasCount()) {
                metrics.add(buildTimeSeries(meterName + "_count", snap.getLabels(), snap.getCount(), timestamp));
            }
            if (snap.hasClassicHistogramData()) {
                metrics.addAll(processClassicHistogramData(snap, meterName, timestamp));
            }
        });
        return metrics;
    }

    private List<TimeSeries> processClassicHistogramData(HistogramSnapshot.HistogramDataPointSnapshot snap,
                                                         String meterName, long timestamp) {
        List<TimeSeries> metrics = new ArrayList<>();
        long cumulativeCount = 0;
        ClassicHistogramBuckets buckets = snap.getClassicBuckets();
        for (int i = 0; i < buckets.size(); i++) {
            cumulativeCount += buckets.getCount(i);
            double upperBound = buckets.getUpperBound(i);
            Labels labels = snap.getLabels().add("le", String.valueOf(upperBound));
            metrics.add(buildTimeSeries(meterName + "_bucket", labels, cumulativeCount, timestamp));
        }
        return metrics;
    }

    private List<TimeSeries> processInfoSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        InfoSnapshot infoSnapshot = (InfoSnapshot) snapshot;
        return infoSnapshot.getDataPoints().stream()
                .map(snap -> buildTimeSeries(meterName + "_info", snap.getLabels(), 1, timestamp))
                .toList();
    }

    private List<TimeSeries> processStateSetSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        StateSetSnapshot stateSetSnapshot = (StateSetSnapshot) snapshot;
        List<TimeSeries> metrics = new ArrayList<>();
        stateSetSnapshot.getDataPoints().forEach(snap -> {
            for (int i = 0; i < snap.size(); i++) {
                Labels labels = snap.getLabels().add("state", snap.getName(i));
                double value = snap.isTrue(i) ? 1 : 0;
                metrics.add(buildTimeSeries(meterName + "_state", labels, value, timestamp));
            }
        });
        return metrics;
    }


    private List<TimeSeries> processUnknownSnapshot(MetricSnapshot snapshot, String meterName, long timestamp) {
        UnknownSnapshot unknownSnapshot = (UnknownSnapshot) snapshot;
        return unknownSnapshot.getDataPoints().stream()
                .map(snap -> buildTimeSeries(meterName + "_untyped", snap.getLabels(), snap.getValue(), timestamp))
                .toList();
    }

    private TimeSeries buildTimeSeries(String name, Labels labels, double value, long timestamp) {
        TimeSeries.Builder builder = TimeSeries.newBuilder();
        if (labels != null) {
            labels.forEach(label -> builder.addLabels(
                    Label.newBuilder()
                            .setName(label.getName())
                            .setValue(label.getValue())
                            .build()
            ));
        }

        if (systemInfo.getInstance() != null) {
            builder.addLabels(Label.newBuilder()
                    .setName("instance")
                    .setValue(systemInfo.getInstance())
                    .build()
            );
        }
        return builder
                .addLabels(Label.newBuilder()
                        .setName("group")
                        .setValue(systemInfo.getGroup())
                        .build())
                .addLabels(Label.newBuilder()
                        .setName("system")
                        .setValue(systemInfo.getSystem())
                        .build())
                .addLabels(Label.newBuilder()
                        .setName("env")
                        .setValue(systemInfo.getEnv())
                        .build())
                .addLabels(Label.newBuilder()
                        .setName("__name__")
                        .setValue(name)
                        .build())
                .addSamples(Sample.newBuilder()
                        .setValue(value)
                        .setTimestamp(timestamp)
                        .build())
                .build();
    }

    @Override
    public void close() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
