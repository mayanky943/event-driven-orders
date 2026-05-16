package com.mayanky943.orders.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEntityTest {

    @Test
    void terminalStatesIdentified() {
        assertThat(buildWithStatus(OrderStatus.CONFIRMED).isTerminal()).isTrue();
        assertThat(buildWithStatus(OrderStatus.CANCELLED).isTerminal()).isTrue();
    }

    @Test
    void nonTerminalStates() {
        assertThat(buildWithStatus(OrderStatus.PENDING).isTerminal()).isFalse();
        assertThat(buildWithStatus(OrderStatus.INVENTORY_RESERVED).isTerminal()).isFalse();
        assertThat(buildWithStatus(OrderStatus.PAYMENT_PROCESSED).isTerminal()).isFalse();
        assertThat(buildWithStatus(OrderStatus.FAILED).isTerminal()).isFalse();
    }

    @Test
    void addLineLinksBackReference() {
        Order order = buildWithStatus(OrderStatus.PENDING);
        OrderLine line = OrderLine.builder()
                .id(UUID.randomUUID()).sku("SKU-A").quantity(1).unitPrice(BigDecimal.ONE).build();
        order.addLine(line);
        assertThat(line.getOrder()).isSameAs(order);
        assertThat(order.getLines()).containsExactly(line);
    }

    private Order buildWithStatus(OrderStatus s) {
        return Order.builder()
                .id(UUID.randomUUID())
                .customerId("c")
                .status(s)
                .totalAmount(BigDecimal.TEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
