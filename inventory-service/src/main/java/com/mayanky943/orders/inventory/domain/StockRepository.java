package com.mayanky943.orders.inventory.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface StockRepository extends JpaRepository<StockItem, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockItem> findBySku(String sku);
}
