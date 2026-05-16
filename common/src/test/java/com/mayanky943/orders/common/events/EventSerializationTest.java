package com.mayanky943.orders.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @Test
    void orderCreatedRoundTrip() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("cust-1")
                .lines(List.of(OrderCreatedEvent.OrderLine.builder()
                        .sku("SKU-A").quantity(2).unitPrice(new BigDecimal("9.99"))
                        .build()))
                .totalAmount(new BigDecimal("19.98"))
                .occurredAt(Instant.parse("2026-05-16T12:00:00Z"))
                .build();

        String json = mapper.writeValueAsString(event);
        OrderCreatedEvent back = mapper.readValue(json, OrderCreatedEvent.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.getLines()).hasSize(1);
        assertThat(back.getLines().get(0).getSku()).isEqualTo("SKU-A");
    }

    @Test
    void inventoryReservedRoundTrip() throws Exception {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .build();
        assertThat(mapper.readValue(mapper.writeValueAsString(event), InventoryReservedEvent.class))
                .isEqualTo(event);
    }

    @Test
    void inventoryFailedCarriesReason() throws Exception {
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reason("Out of stock")
                .occurredAt(Instant.now())
                .build();
        InventoryReservationFailedEvent back = mapper.readValue(
                mapper.writeValueAsString(event), InventoryReservationFailedEvent.class);
        assertThat(back.getReason()).isEqualTo("Out of stock");
    }

    @Test
    void paymentProcessedRoundTrip() throws Exception {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("99.99"))
                .occurredAt(Instant.now())
                .build();
        assertThat(mapper.readValue(mapper.writeValueAsString(event), PaymentProcessedEvent.class))
                .isEqualTo(event);
    }

    @Test
    void paymentFailedCarriesReason() throws Exception {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .reason("card declined")
                .occurredAt(Instant.now())
                .build();
        assertThat(mapper.readValue(mapper.writeValueAsString(event), PaymentFailedEvent.class).getReason())
                .isEqualTo("card declined");
    }

    @Test
    void orderConfirmedRoundTrip() throws Exception {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .occurredAt(Instant.now())
                .build();
        assertThat(mapper.readValue(mapper.writeValueAsString(event), OrderConfirmedEvent.class))
                .isEqualTo(event);
    }

    @Test
    void orderCancelledRoundTrip() throws Exception {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .reason("payment failed")
                .occurredAt(Instant.now())
                .build();
        assertThat(mapper.readValue(mapper.writeValueAsString(event), OrderCancelledEvent.class))
                .isEqualTo(event);
    }

    @Test
    void allEventsImplementDomainEvent() {
        OrderCreatedEvent e = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID()).orderId(UUID.randomUUID()).build();
        assertThat(e).isInstanceOf(DomainEvent.class);
    }
}
