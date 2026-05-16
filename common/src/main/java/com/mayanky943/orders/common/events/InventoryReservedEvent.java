package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class InventoryReservedEvent implements DomainEvent {
    UUID eventId;
    UUID orderId;
    UUID reservationId;
    Instant occurredAt;

    @JsonCreator
    public InventoryReservedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("reservationId") UUID reservationId,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.occurredAt = occurredAt;
    }
}
