package com.mayanky943.orders.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.InventoryReservedEvent;
import com.mayanky943.orders.common.events.PaymentFailedEvent;
import com.mayanky943.orders.common.events.PaymentProcessedEvent;
import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxService;
import com.mayanky943.orders.payment.domain.Payment;
import com.mayanky943.orders.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaHandler {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final IdempotencyService idempotency;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "payment-service")
    @Transactional
    public void onInventoryReserved(String payload) throws Exception {
        InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
        if (!idempotency.tryAcquire("payment:" + event.getEventId())) return;

        if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.info("Payment already exists for order {}, skipping", event.getOrderId());
            return;
        }

        // In reality we'd look up the order total. Inventory.reserved doesn't
        // carry it, so for the demo we charge a placeholder amount derived
        // from a deterministic order-id hash — good enough to exercise the saga.
        BigDecimal amount = derivePlaceholderAmount(event.getOrderId());
        PaymentGateway.Result result = gateway.charge(event.getOrderId(), amount);

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(event.getOrderId())
                .amount(amount)
                .status(result.success() ? Payment.Status.PROCESSED : Payment.Status.FAILED)
                .failureReason(result.failureReason())
                .createdAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        if (result.success()) {
            PaymentProcessedEvent processed = PaymentProcessedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(event.getOrderId())
                    .paymentId(payment.getId())
                    .amount(amount)
                    .occurredAt(Instant.now())
                    .build();
            outboxService.enqueue("Payment", event.getOrderId(),
                    KafkaTopics.PAYMENT_PROCESSED, processed);
            log.info("Payment PROCESSED for order {}", event.getOrderId());
        } else {
            PaymentFailedEvent failed = PaymentFailedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(event.getOrderId())
                    .reason(result.failureReason())
                    .occurredAt(Instant.now())
                    .build();
            outboxService.enqueue("Payment", event.getOrderId(),
                    KafkaTopics.PAYMENT_FAILED, failed);
            log.info("Payment FAILED for order {}: {}", event.getOrderId(), result.failureReason());
        }
    }

    private BigDecimal derivePlaceholderAmount(UUID orderId) {
        long hash = Math.abs(orderId.getMostSignificantBits() % 1000L);
        return BigDecimal.valueOf(10 + hash / 10.0);
    }
}
