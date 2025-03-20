package ru.mirea.prometheus.exporter;

import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

@Slf4j
public class PrometheusRemoteWriteClient {

    private final String apiUrl;
    private final String userAgent;
    private final String authorization;
    private final HttpClient httpClient;

    public PrometheusRemoteWriteClient(String apiUrl, String userAgent, String authorization) {
        this.apiUrl = apiUrl;
        this.userAgent = userAgent;
        this.authorization = authorization;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void write(RemoteWriteRequest.WriteRequest writeRequest) {
        try {
            byte[] uncompressed = writeRequest.toByteArray();
            byte[] compressed = new byte[Snappy.maxCompressedLength(uncompressed.length)];
            int compressedLength = Snappy.compress(uncompressed, 0, uncompressed.length, compressed, 0);
            byte[] body = Arrays.copyOf(compressed, compressedLength);

            var httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .headers(
                            "Authorization", "Bearer " + authorization,
                            "Content-Encoding", "snappy",
                            "Content-Type", "application/x-protobuf",
                            "User-Agent", userAgent,
                            "X-Prometheus-Remote-Write-Version", "0.1.0"
                    )
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Failed to write metrics. Status Code: {}, Response Body: {}",
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Unable to send remote-write request to {} due to exception", apiUrl, e);
        }
    }
}
