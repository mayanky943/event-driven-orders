package com.mayanky943.orders.order.service;

import com.mayanky943.orders.common.outbox.OutboxEvent;
import com.mayanky943.orders.common.outbox.OutboxPublisher;
import com.mayanky943.orders.common.outbox.OutboxRepository;
import com.mayanky943.orders.order.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OutboxRepository repository;
    @Autowired private OutboxPublisher publisher;

    @Test
    void unpublishedEventIsEmittedAndMarkedPublished() {
        UUID orderId = UUID.randomUUID();
        OutboxEvent record = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("OrderCreatedEvent")
                .topic("orders.created.test")
                .payload("{\"orderId\":\"" + orderId + "\"}")
                .published(false)
                .createdAt(Instant.now())
                .attempts(0)
                .build();
        repository.save(record);

        publisher.publishBatch();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent reloaded = repository.findById(record.getId()).orElseThrow();
            assertThat(reloaded.isPublished()).isTrue();
            assertThat(reloaded.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void multipleEventsAllGetPublished() {
        for (int i = 0; i < 5; i++) {
            repository.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("Order")
                    .aggregateId(UUID.randomUUID())
                    .eventType("OrderCreatedEvent")
                    .topic("orders.created.test.batch")
                    .payload("{}")
                    .published(false)
                    .createdAt(Instant.now())
                    .attempts(0)
                    .build());
        }

        publisher.publishBatch();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.countByPublishedFalse()).isZero());
    }
}
