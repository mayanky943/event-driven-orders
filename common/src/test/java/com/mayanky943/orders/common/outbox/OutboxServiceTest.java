package com.mayanky943.orders.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mayanky943.orders.common.events.OrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    private OutboxRepository repository;
    private ObjectMapper mapper;
    private OutboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new OutboxService(repository, mapper);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enqueueSerializesEventToPayload() {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .occurredAt(Instant.now())
                .build();

        OutboxEvent record = service.enqueue("Order", event.getOrderId(), "orders.confirmed", event);

        assertThat(record.getTopic()).isEqualTo("orders.confirmed");
        assertThat(record.getAggregateType()).isEqualTo("Order");
        assertThat(record.getAggregateId()).isEqualTo(event.getOrderId());
        assertThat(record.getEventType()).isEqualTo("OrderConfirmedEvent");
        assertThat(record.getPayload()).contains(event.getCustomerId());
        assertThat(record.isPublished()).isFalse();
    }

    @Test
    void enqueueDefaultsIdFromEventId() {
        UUID id = UUID.randomUUID();
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(id).orderId(UUID.randomUUID()).build();

        OutboxEvent record = service.enqueue("Order", event.getOrderId(), "topic", event);
        assertThat(record.getId()).isEqualTo(id);
    }

    @Test
    void enqueueGeneratesIdWhenEventIdNull() {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .orderId(UUID.randomUUID()).build();
        OutboxEvent record = service.enqueue("Order", event.getOrderId(), "topic", event);
        assertThat(record.getId()).isNotNull();
    }

    @Test
    void enqueuePersistsViaRepository() {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID()).orderId(UUID.randomUUID()).build();
        service.enqueue("Order", event.getOrderId(), "topic", event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getAttempts()).isZero();
    }
}
