package ru.mirea.prometheus.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.OffsetDateTime;

@Slf4j
@Getter
@Setter
@SpringBootApplication
@RequiredArgsConstructor
public class Consumer {

    @Value("${HOSTNAME:unknown}")
    private String hostname;

    private final MessageLogRepository messageLogRepository;

    public static void main(String[] args) {
        SpringApplication.run(Consumer.class, args);
    }

    @KafkaListener(topics = "demo-topic", groupId = "group-id")
    public void listen(String message) {
        MessageLog logEntry = new MessageLog(
                null,
                message,
                OffsetDateTime.now(),
                hostname
        );
        messageLogRepository.save(logEntry);

        log.info("{} received message: {}", hostname, message);
    }
}
