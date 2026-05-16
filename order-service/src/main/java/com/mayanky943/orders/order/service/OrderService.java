package com.mayanky943.orders.order.service;

import com.mayanky943.orders.common.events.OrderCreatedEvent;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxService;
import com.mayanky943.orders.order.api.CreateOrderRequest;
import com.mayanky943.orders.order.domain.Order;
import com.mayanky943.orders.order.domain.OrderLine;
import com.mayanky943.orders.order.domain.OrderRepository;
import com.mayanky943.orders.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    /**
     * Single TX:
     *   1. Persist Order (PENDING)
     *   2. Persist OutboxEvent (OrderCreatedEvent)
     * The polling publisher reads the outbox and emits to Kafka separately.
     * That's the entire point of the outbox pattern — no dual-write.
     */
    @Transactional
    public Order create(CreateOrderRequest request) {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal total = request.getLines().stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .id(orderId)
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .createdAt(now)
                .updatedAt(now)
                .build();

        for (CreateOrderRequest.Line line : request.getLines()) {
            order.addLine(OrderLine.builder()
                    .id(UUID.randomUUID())
                    .sku(line.getSku())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .build());
        }

        orderRepository.save(order);

        List<OrderCreatedEvent.OrderLine> eventLines = request.getLines().stream()
                .map(l -> OrderCreatedEvent.OrderLine.builder()
                        .sku(l.getSku())
                        .quantity(l.getQuantity())
                        .unitPrice(l.getUnitPrice())
                        .build())
                .toList();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(orderId)
                .customerId(request.getCustomerId())
                .lines(eventLines)
                .totalAmount(total)
                .occurredAt(now)
                .build();

        outboxService.enqueue("Order", orderId, KafkaTopics.ORDERS_CREATED, event);

        log.info("Order {} created (PENDING)", orderId);
        return order;
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Order not found: " + id));
    }
}
