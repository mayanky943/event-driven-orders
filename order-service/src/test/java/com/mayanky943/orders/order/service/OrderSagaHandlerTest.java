package com.mayanky943.orders.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.*;
import com.mayanky943.orders.common.outbox.OutboxEvent;
import com.mayanky943.orders.common.outbox.OutboxRepository;
import com.mayanky943.orders.order.AbstractIntegrationTest;
import com.mayanky943.orders.order.domain.Order;
import com.mayanky943.orders.order.domain.OrderRepository;
import com.mayanky943.orders.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSagaHandlerTest extends AbstractIntegrationTest {

    @Autowired private OrderSagaHandler handler;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void cleanRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void inventoryReservedAdvancesPendingToInventoryReserved() throws Exception {
        Order order = persistOrder(OrderStatus.PENDING);
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .reservationId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryReserved(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    @Test
    void paymentProcessedAdvancesToConfirmedAndEmitsEvent() throws Exception {
        Order order = persistOrder(OrderStatus.INVENTORY_RESERVED);
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("12.50"))
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onPaymentProcessed(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(outboxRepository.findAll()).anySatisfy(o ->
                assertThat(o.getTopic()).isEqualTo("orders.confirmed"));
    }

    @Test
    void paymentFailedCancelsOrderAndEmitsCancelledEvent() throws Exception {
        Order order = persistOrder(OrderStatus.INVENTORY_RESERVED);
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .reason("declined")
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onPaymentFailed(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(updated.getFailureReason()).isEqualTo("declined");

        assertThat(outboxRepository.findAll()).anySatisfy(o ->
                assertThat(o.getTopic()).isEqualTo("orders.cancelled"));
    }

    @Test
    void inventoryFailedCancelsOrder() throws Exception {
        Order order = persistOrder(OrderStatus.PENDING);
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .reason("out of stock")
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onInventoryFailed(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void duplicateEventIsIdempotent() throws Exception {
        Order order = persistOrder(OrderStatus.INVENTORY_RESERVED);
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .paymentId(UUID.randomUUID())
                .amount(BigDecimal.TEN)
                .occurredAt(Instant.now())
                .build();
        String payload = mapper.writeValueAsString(event);

        tx.executeWithoutResult(s -> {
            try { handler.onPaymentProcessed(payload); } catch (Exception e) { throw new RuntimeException(e); }
        });
        long firstOutboxCount = outboxRepository.count();

        tx.executeWithoutResult(s -> {
            try { handler.onPaymentProcessed(payload); } catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(outboxRepository.count()).isEqualTo(firstOutboxCount);
    }

    @Test
    void terminalOrderIgnoresFurtherEvents() throws Exception {
        Order order = persistOrder(OrderStatus.CANCELLED);
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .paymentId(UUID.randomUUID())
                .amount(BigDecimal.TEN)
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onPaymentProcessed(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    private Order persistOrder(OrderStatus status) {
        Order o = Order.builder()
                .id(UUID.randomUUID())
                .customerId("cust-1")
                .status(status)
                .totalAmount(new BigDecimal("10.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return orderRepository.save(o);
    }
}
