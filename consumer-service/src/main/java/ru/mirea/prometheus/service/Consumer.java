package ru.mirea.prometheus.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

@Slf4j
@SpringBootApplication
public class Consumer {

    public static void main(String[] args) {
        SpringApplication.run(Consumer.class, args);
    }

    @KafkaListener(topics = "demo-topic", groupId = "group-id")
    public void listen(String message, @Value("${HOSTNAME:unknown}") String hostname) {
        log.info("{} received message: {}", hostname, message);
    }
}
