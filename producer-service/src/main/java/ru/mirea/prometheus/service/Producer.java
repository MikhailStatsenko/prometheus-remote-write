package ru.mirea.prometheus.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@SpringBootApplication
public class Producer {

    public static void main(String[] args) {
        SpringApplication.run(Producer.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(KafkaTemplate<String, String> kafkaTemplate,
                                               @Value("${HOSTNAME:unknown}") String hostname) {
        return args -> {
            while (true) {
                String key = UUID.randomUUID().toString();
                String msg = "Hello from " + hostname + " @ " + System.currentTimeMillis();
                CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("demo-topic", key, msg);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message: {}", msg, ex);
                    } else {
                        RecordMetadata metadata = result.getRecordMetadata();
                        log.info("Sent to partition {} (offset {}): {}", metadata.partition(), metadata.offset(), msg);
                    }
                });
                Thread.sleep(1000);
            }
        };
    }
}

