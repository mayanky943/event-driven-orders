package com.mayanky943.orders.inventory.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    private long version;

    public boolean canReserve(int qty) {
        return available >= qty;
    }

    public void reserve(int qty) {
        if (!canReserve(qty)) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        available -= qty;
        reserved += qty;
    }

    public void release(int qty) {
        int amount = Math.min(qty, reserved);
        reserved -= amount;
        available += amount;
    }
}
