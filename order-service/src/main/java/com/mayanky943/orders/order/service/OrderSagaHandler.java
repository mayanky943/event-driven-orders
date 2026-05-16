package com.mayanky943.orders.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.*;
import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxService;
import com.mayanky943.orders.order.domain.Order;
import com.mayanky943.orders.order.domain.OrderRepository;
import com.mayanky943.orders.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reacts to events from downstream services and advances the order
 * through the saga state machine.
 *
 * Choreography: each service publishes facts; the Order service just
 * watches for terminal facts (payment.processed / payment.failed /
 * inventory.reservation.failed) and updates the order accordingly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaHandler {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "order-service")
    @Transactional
    public void onInventoryReserved(String payload) throws Exception {
        InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
        if (!idempotency.tryAcquire("order:" + event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.isTerminal()) {
            log.warn("Ignoring inventory.reserved for unknown/terminal order {}", event.getOrderId());
            return;
        }
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVATION_FAILED, groupId = "order-service")
    @Transactional
    public void onInventoryFailed(String payload) throws Exception {
        InventoryReservationFailedEvent event = objectMapper.readValue(
                payload, InventoryReservationFailedEvent.class);
        if (!idempotency.tryAcquire("order:" + event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.isTerminal()) return;

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason(event.getReason());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        emitOrderCancelled(order, event.getReason());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_PROCESSED, groupId = "order-service")
    @Transactional
    public void onPaymentProcessed(String payload) throws Exception {
        PaymentProcessedEvent event = objectMapper.readValue(payload, PaymentProcessedEvent.class);
        if (!idempotency.tryAcquire("order:" + event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.isTerminal()) return;

        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        OrderConfirmedEvent confirmed = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .occurredAt(Instant.now())
                .build();
        outboxService.enqueue("Order", order.getId(), KafkaTopics.ORDERS_CONFIRMED, confirmed);
        log.info("Order {} CONFIRMED", order.getId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service")
    @Transactional
    public void onPaymentFailed(String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        if (!idempotency.tryAcquire("order:" + event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null || order.isTerminal()) return;

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason(event.getReason());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        emitOrderCancelled(order, event.getReason());
    }

    private void emitOrderCancelled(Order order, String reason) {
        OrderCancelledEvent cancelled = OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        outboxService.enqueue("Order", order.getId(), KafkaTopics.ORDERS_CANCELLED, cancelled);
        log.info("Order {} CANCELLED ({})", order.getId(), reason);
    }
}
