package com.mayanky943.orders.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.InventoryReservationFailedEvent;
import com.mayanky943.orders.common.events.InventoryReservedEvent;
import com.mayanky943.orders.common.events.OrderCancelledEvent;
import com.mayanky943.orders.common.events.OrderCreatedEvent;
import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxService;
import com.mayanky943.orders.inventory.domain.Reservation;
import com.mayanky943.orders.inventory.domain.ReservationRepository;
import com.mayanky943.orders.inventory.domain.StockItem;
import com.mayanky943.orders.inventory.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySagaHandler {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxService outboxService;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDERS_CREATED, groupId = "inventory-service")
    @Transactional
    public void onOrderCreated(String payload) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        if (!idempotency.tryAcquire("inventory:" + event.getEventId())) return;

        List<Reservation> made = new ArrayList<>();
        try {
            for (OrderCreatedEvent.OrderLine line : event.getLines()) {
                StockItem item = stockRepository.findBySku(line.getSku())
                        .orElseThrow(() -> new IllegalStateException("Unknown SKU " + line.getSku()));
                item.reserve(line.getQuantity());
                stockRepository.save(item);

                Reservation r = Reservation.builder()
                        .id(UUID.randomUUID())
                        .orderId(event.getOrderId())
                        .sku(line.getSku())
                        .quantity(line.getQuantity())
                        .status(Reservation.ReservationStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .build();
                reservationRepository.save(r);
                made.add(r);
            }
            UUID reservationId = made.isEmpty() ? UUID.randomUUID() : made.get(0).getId();

            InventoryReservedEvent reserved = InventoryReservedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(event.getOrderId())
                    .reservationId(reservationId)
                    .occurredAt(Instant.now())
                    .build();
            outboxService.enqueue("Inventory", event.getOrderId(),
                    KafkaTopics.INVENTORY_RESERVED, reserved);
            log.info("Reserved {} lines for order {}", made.size(), event.getOrderId());
        } catch (Exception ex) {
            log.warn("Inventory reservation failed for order {}: {}", event.getOrderId(), ex.getMessage());
            // Rollback in-memory changes by throwing — JPA will roll back the TX.
            // Then write a fresh failure event in a new TX.
            throw new ReservationException(event.getOrderId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_CANCELLED, groupId = "inventory-service")
    @Transactional
    public void onOrderCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        if (!idempotency.tryAcquire("inventory:" + event.getEventId())) return;

        List<Reservation> active = reservationRepository.findByOrderIdAndStatus(
                event.getOrderId(), Reservation.ReservationStatus.ACTIVE);
        for (Reservation r : active) {
            Optional<StockItem> item = stockRepository.findBySku(r.getSku());
            item.ifPresent(s -> {
                s.release(r.getQuantity());
                stockRepository.save(s);
            });
            r.setStatus(Reservation.ReservationStatus.RELEASED);
            reservationRepository.save(r);
        }
        log.info("Released {} reservations for cancelled order {}", active.size(), event.getOrderId());
    }
}
