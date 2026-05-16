package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class OrderConfirmedEvent implements DomainEvent {
    UUID eventId;
    UUID orderId;
    String customerId;
    Instant occurredAt;

    @JsonCreator
    public OrderConfirmedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.occurredAt = occurredAt;
    }
}
