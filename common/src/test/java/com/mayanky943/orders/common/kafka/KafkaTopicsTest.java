package com.mayanky943.orders.common.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicsTest {

    @Test
    void dlqSuffixIsAppended() {
        assertThat(KafkaTopics.dlqOf("orders.created"))
                .isEqualTo("orders.created.dlq");
    }

    @Test
    void allBusinessTopicsDefined() {
        assertThat(KafkaTopics.ORDERS_CREATED).isEqualTo("orders.created");
        assertThat(KafkaTopics.INVENTORY_RESERVED).isEqualTo("inventory.reserved");
        assertThat(KafkaTopics.INVENTORY_RESERVATION_FAILED).isEqualTo("inventory.reservation.failed");
        assertThat(KafkaTopics.PAYMENT_PROCESSED).isEqualTo("payment.processed");
        assertThat(KafkaTopics.PAYMENT_FAILED).isEqualTo("payment.failed");
        assertThat(KafkaTopics.ORDERS_CONFIRMED).isEqualTo("orders.confirmed");
        assertThat(KafkaTopics.ORDERS_CANCELLED).isEqualTo("orders.cancelled");
    }

    @Test
    void topicNamesAreSnakeFreeForKafkaCompat() {
        // Kafka topics shouldn't contain underscores by convention — dotted is fine
        for (String t : new String[]{
                KafkaTopics.ORDERS_CREATED, KafkaTopics.INVENTORY_RESERVED,
                KafkaTopics.INVENTORY_RESERVATION_FAILED, KafkaTopics.PAYMENT_PROCESSED,
                KafkaTopics.PAYMENT_FAILED, KafkaTopics.ORDERS_CONFIRMED,
                KafkaTopics.ORDERS_CANCELLED }) {
            assertThat(t).doesNotContain("_");
        }
    }
}
