package com.mayanky943.orders.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.OrderCancelledEvent;
import com.mayanky943.orders.common.events.OrderCreatedEvent;
import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxRepository;
import com.mayanky943.orders.inventory.AbstractIntegrationTest;
import com.mayanky943.orders.inventory.domain.Reservation;
import com.mayanky943.orders.inventory.domain.ReservationRepository;
import com.mayanky943.orders.inventory.domain.StockItem;
import com.mayanky943.orders.inventory.domain.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventorySagaHandlerTest extends AbstractIntegrationTest {

    @Autowired private InventorySagaHandler handler;
    @Autowired private StockRepository stockRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        if (stockRepository.findById("SKU-TEST").isEmpty()) {
            stockRepository.save(StockItem.builder().sku("SKU-TEST").available(10).reserved(0).build());
        }
    }

    @Test
    void successfulReservationDecrementsStockAndEmitsReservedEvent() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c")
                .lines(List.of(OrderCreatedEvent.OrderLine.builder()
                        .sku("SKU-TEST").quantity(3).unitPrice(BigDecimal.ONE).build()))
                .totalAmount(new BigDecimal("3"))
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onOrderCreated(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        StockItem item = stockRepository.findById("SKU-TEST").orElseThrow();
        assertThat(item.getAvailable()).isEqualTo(7);
        assertThat(item.getReserved()).isEqualTo(3);

        assertThat(outboxRepository.findAll())
                .anySatisfy(o -> assertThat(o.getTopic()).isEqualTo(KafkaTopics.INVENTORY_RESERVED));
    }

    @Test
    void insufficientStockThrowsReservationException() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c")
                .lines(List.of(OrderCreatedEvent.OrderLine.builder()
                        .sku("SKU-TEST").quantity(9999).unitPrice(BigDecimal.ONE).build()))
                .totalAmount(BigDecimal.TEN)
                .occurredAt(Instant.now())
                .build();
        String payload = mapper.writeValueAsString(event);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            try { handler.onOrderCreated(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        })).hasCauseInstanceOf(ReservationException.class);
    }

    @Test
    void cancelledOrderReleasesActiveReservations() throws Exception {
        // First reserve
        OrderCreatedEvent created = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c")
                .lines(List.of(OrderCreatedEvent.OrderLine.builder()
                        .sku("SKU-TEST").quantity(2).unitPrice(BigDecimal.ONE).build()))
                .totalAmount(new BigDecimal("2"))
                .occurredAt(Instant.now())
                .build();
        tx.executeWithoutResult(s -> {
            try { handler.onOrderCreated(mapper.writeValueAsString(created)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        // Then cancel
        OrderCancelledEvent cancelled = OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(created.getOrderId())
                .customerId("c")
                .reason("test")
                .occurredAt(Instant.now())
                .build();
        tx.executeWithoutResult(s -> {
            try { handler.onOrderCancelled(mapper.writeValueAsString(cancelled)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        StockItem item = stockRepository.findById("SKU-TEST").orElseThrow();
        assertThat(item.getReserved()).isZero();

        List<Reservation> released = reservationRepository.findByOrderIdAndStatus(
                created.getOrderId(), Reservation.ReservationStatus.RELEASED);
        assertThat(released).hasSize(1);
    }

    @Test
    void duplicateOrderCreatedIsIdempotent() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c")
                .lines(List.of(OrderCreatedEvent.OrderLine.builder()
                        .sku("SKU-TEST").quantity(1).unitPrice(BigDecimal.ONE).build()))
                .totalAmount(BigDecimal.ONE)
                .occurredAt(Instant.now())
                .build();
        String payload = mapper.writeValueAsString(event);

        tx.executeWithoutResult(s -> {
            try { handler.onOrderCreated(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        int reservedBefore = stockRepository.findById("SKU-TEST").orElseThrow().getReserved();

        tx.executeWithoutResult(s -> {
            try { handler.onOrderCreated(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        // Stock should not have moved again
        assertThat(stockRepository.findById("SKU-TEST").orElseThrow().getReserved())
                .isEqualTo(reservedBefore);
    }
}
