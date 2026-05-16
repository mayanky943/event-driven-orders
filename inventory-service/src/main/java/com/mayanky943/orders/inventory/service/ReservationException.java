package com.mayanky943.orders.inventory.service;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ReservationException extends RuntimeException {
    private final UUID orderId;

    public ReservationException(UUID orderId, String message) {
        super(message);
        this.orderId = orderId;
    }
}
