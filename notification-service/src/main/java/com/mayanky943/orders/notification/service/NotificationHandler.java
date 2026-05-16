package com.mayanky943.orders.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.OrderCancelledEvent;
import com.mayanky943.orders.common.events.OrderConfirmedEvent;
import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.notification.domain.Notification;
import com.mayanky943.orders.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHandler {

    private final NotificationRepository repository;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDERS_CONFIRMED, groupId = "notification-service")
    @Transactional
    public void onConfirmed(String payload) throws Exception {
        OrderConfirmedEvent event = objectMapper.readValue(payload, OrderConfirmedEvent.class);
        if (!idempotency.tryAcquire("notif:" + event.getEventId())) return;

        repository.save(Notification.builder()
                .id(UUID.randomUUID())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .channel(Notification.Channel.EMAIL)
                .type("ORDER_CONFIRMED")
                .message("Your order " + event.getOrderId() + " has been confirmed.")
                .createdAt(Instant.now())
                .build());
        log.info("Notification: order {} confirmed", event.getOrderId());
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_CANCELLED, groupId = "notification-service")
    @Transactional
    public void onCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        if (!idempotency.tryAcquire("notif:" + event.getEventId())) return;

        repository.save(Notification.builder()
                .id(UUID.randomUUID())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .channel(Notification.Channel.EMAIL)
                .type("ORDER_CANCELLED")
                .message("Your order " + event.getOrderId() + " was cancelled: " + event.getReason())
                .createdAt(Instant.now())
                .build());
        log.info("Notification: order {} cancelled", event.getOrderId());
    }
}
