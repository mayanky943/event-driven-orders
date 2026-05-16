package com.mayanky943.orders.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "poison_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoisonMessage {

    @Id
    private UUID id;

    @Column(name = "original_topic", nullable = false, length = 128)
    private String originalTopic;

    @Column(nullable = false)
    private int partition;

    @Column(name = "offset_value", nullable = false)
    private long offsetValue;

    @Column(name = "message_key", length = 256)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "exception_message", columnDefinition = "text")
    private String exceptionMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
