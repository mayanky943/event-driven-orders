package com.mayanky943.orders.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockItemTest {

    @Test
    void canReserveWhenSufficientAvailable() {
        StockItem item = StockItem.builder().sku("A").available(10).reserved(0).build();
        assertThat(item.canReserve(5)).isTrue();
    }

    @Test
    void cannotReserveWhenInsufficient() {
        StockItem item = StockItem.builder().sku("A").available(2).reserved(0).build();
        assertThat(item.canReserve(5)).isFalse();
    }

    @Test
    void reserveDecrementsAvailableAndIncrementsReserved() {
        StockItem item = StockItem.builder().sku("A").available(10).reserved(0).build();
        item.reserve(3);
        assertThat(item.getAvailable()).isEqualTo(7);
        assertThat(item.getReserved()).isEqualTo(3);
    }

    @Test
    void reserveThrowsWhenInsufficient() {
        StockItem item = StockItem.builder().sku("A").available(2).reserved(0).build();
        assertThatThrownBy(() -> item.reserve(5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releaseMovesQtyFromReservedBackToAvailable() {
        StockItem item = StockItem.builder().sku("A").available(7).reserved(3).build();
        item.release(2);
        assertThat(item.getAvailable()).isEqualTo(9);
        assertThat(item.getReserved()).isEqualTo(1);
    }

    @Test
    void releaseCappedAtReservedQuantity() {
        StockItem item = StockItem.builder().sku("A").available(5).reserved(2).build();
        item.release(10);
        assertThat(item.getAvailable()).isEqualTo(7);
        assertThat(item.getReserved()).isZero();
    }
}
