package ru.mirea.prometheus.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.mirea.prometheus.exporter.MetricExporter;
import ru.mirea.prometheus.exporter.PrometheusRemoteWriteClient;
import ru.mirea.prometheus.exporter.SystemInfo;

@Configuration
@Profile({"test", "prod"})
public class MetricsConfig {

    @Value("${metrics.api-url}")
    private String apiUrl;
    @Value("${metrics.token}")
    private String token;
    @Value("${spring.application.name}")
    private String userAgent;

    @Value("${metrics.group}")
    private String group;
    @Value("${spring.application.name}")
    private String system;
    @Value("${spring.profiles.active}")
    private String env;
    @Value("${HOSTNAME:unknown}")
    private String hostname;

    @Bean
    public PrometheusRemoteWriteClient
    prometheusRemoteWriteClient() {
        return new PrometheusRemoteWriteClient(apiUrl, token, userAgent);
    }

    @Bean
    public MetricExporter metricExporter(PrometheusMeterRegistry prometheusMeterRegistry,
                                         PrometheusRemoteWriteClient prometheusRemoteWriteClient) {
        return new MetricExporter(
                new SystemInfo(group, system, env, hostname),
                10,
                prometheusMeterRegistry,
                prometheusRemoteWriteClient
        );
    }
}
