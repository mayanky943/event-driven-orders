package com.mayanky943.orders.order.domain;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PROCESSED,
    CONFIRMED,
    CANCELLED,
    FAILED
}
