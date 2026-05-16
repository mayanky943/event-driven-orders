package com.mayanky943.orders.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.InventoryReservedEvent;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxRepository;
import com.mayanky943.orders.payment.AbstractIntegrationTest;
import com.mayanky943.orders.payment.domain.Payment;
import com.mayanky943.orders.payment.domain.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSagaHandlerTest extends AbstractIntegrationTest {

    @Autowired private PaymentSagaHandler handler;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private PaymentGateway gateway;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void inventoryReservedSuccessChargesAndEmitsProcessedEvent() throws Exception {
        ReflectionTestUtils.setField(gateway, "failureRate", 0.0);

        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.PROCESSED);

        assertThat(outboxRepository.findAll())
                .anySatisfy(o -> assertThat(o.getTopic()).isEqualTo(KafkaTopics.PAYMENT_PROCESSED));
    }

    @Test
    void inventoryReservedFailureEmitsFailedEvent() throws Exception {
        ReflectionTestUtils.setField(gateway, "failureRate", 1.0);

        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
        assertThat(payment.getFailureReason()).isNotBlank();

        assertThat(outboxRepository.findAll())
                .anySatisfy(o -> assertThat(o.getTopic()).isEqualTo(KafkaTopics.PAYMENT_FAILED));
    }

    @Test
    void duplicateInventoryReservedIsIdempotent() throws Exception {
        ReflectionTestUtils.setField(gateway, "failureRate", 0.0);

        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .build();
        String payload = mapper.writeValueAsString(event);

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        long countBefore = paymentRepository.count();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(paymentRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void existingPaymentForOrderIsSkipped() throws Exception {
        ReflectionTestUtils.setField(gateway, "failureRate", 0.0);

        // Two distinct events for the same orderId; the second should be a no-op.
        UUID orderId = UUID.randomUUID();
        InventoryReservedEvent first = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID()).orderId(orderId)
                .reservationId(UUID.randomUUID()).occurredAt(Instant.now()).build();
        InventoryReservedEvent second = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID()).orderId(orderId)
                .reservationId(UUID.randomUUID()).occurredAt(Instant.now()).build();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(mapper.writeValueAsString(first)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(mapper.writeValueAsString(second)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
        // Unique constraint on order_id guarantees only one payment row
    }
}
