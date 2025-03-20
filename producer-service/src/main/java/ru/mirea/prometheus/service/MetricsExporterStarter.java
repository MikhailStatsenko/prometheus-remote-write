package ru.mirea.prometheus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.mirea.prometheus.exporter.MetricExporter;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "metrics", name = "metrics-exporter-enabled", havingValue = "true")
public class MetricsExporterStarter {

    private final MetricExporter metricExporter;

    @EventListener(ApplicationReadyEvent.class)
    public void startSendingMetrics() {
        metricExporter.startSendingMetrics();
    }
}
