package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class PaymentFailedEvent implements DomainEvent {
    UUID eventId;
    UUID orderId;
    String reason;
    Instant occurredAt;

    @JsonCreator
    public PaymentFailedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("reason") String reason,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
