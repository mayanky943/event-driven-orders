package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class PaymentProcessedEvent implements DomainEvent {
    UUID eventId;
    UUID orderId;
    UUID paymentId;
    BigDecimal amount;
    Instant occurredAt;

    @JsonCreator
    public PaymentProcessedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("paymentId") UUID paymentId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }
}
