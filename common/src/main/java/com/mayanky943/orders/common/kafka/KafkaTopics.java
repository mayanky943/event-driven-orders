package com.mayanky943.orders.common.kafka;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDERS_CREATED = "orders.created";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation.failed";
    public static final String PAYMENT_PROCESSED = "payment.processed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDERS_CONFIRMED = "orders.confirmed";
    public static final String ORDERS_CANCELLED = "orders.cancelled";

    public static final String DLQ_SUFFIX = ".dlq";

    public static String dlqOf(String topic) {
        return topic + DLQ_SUFFIX;
    }
}
