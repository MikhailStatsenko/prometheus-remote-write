package ru.mirea.prometheus.service;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "message_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"received_at", "id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String message;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    private String hostname;
}
