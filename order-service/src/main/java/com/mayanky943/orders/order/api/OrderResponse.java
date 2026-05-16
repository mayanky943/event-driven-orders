package com.mayanky943.orders.order.api;

import com.mayanky943.orders.order.domain.Order;
import com.mayanky943.orders.order.domain.OrderStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class OrderResponse {
    UUID id;
    String customerId;
    OrderStatus status;
    BigDecimal totalAmount;
    Instant createdAt;
    Instant updatedAt;
    String failureReason;

    public static OrderResponse from(Order o) {
        return OrderResponse.builder()
                .id(o.getId())
                .customerId(o.getCustomerId())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .failureReason(o.getFailureReason())
                .build();
    }
}
