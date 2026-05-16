package com.mayanky943.orders.inventory.service;

import com.mayanky943.orders.common.events.InventoryReservationFailedEvent;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Triggered after the inventory listener TX rolls back. Writes a failure
 * event in a fresh TX so it survives the rollback that just happened.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationFailureEmitter {

    private final OutboxService outboxService;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void emit(UUID orderId, String reason) {
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(orderId)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        outboxService.enqueue("Inventory", orderId, KafkaTopics.INVENTORY_RESERVATION_FAILED, event);
        log.info("Emitted inventory.reservation.failed for order {} ({})", orderId, reason);
    }
}
