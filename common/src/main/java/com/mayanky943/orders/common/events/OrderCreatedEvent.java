package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class OrderCreatedEvent implements DomainEvent {
    UUID eventId;
    UUID orderId;
    String customerId;
    List<OrderLine> lines;
    BigDecimal totalAmount;
    Instant occurredAt;

    @JsonCreator
    public OrderCreatedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("lines") List<OrderLine> lines,
            @JsonProperty("totalAmount") BigDecimal totalAmount,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.lines = lines;
        this.totalAmount = totalAmount;
        this.occurredAt = occurredAt;
    }

    @Value
    @Builder
    public static class OrderLine {
        String sku;
        int quantity;
        BigDecimal unitPrice;

        @JsonCreator
        public OrderLine(
                @JsonProperty("sku") String sku,
                @JsonProperty("quantity") int quantity,
                @JsonProperty("unitPrice") BigDecimal unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
