package com.mayanky943.orders.inventory.api;

import com.mayanky943.orders.inventory.domain.StockItem;
import com.mayanky943.orders.inventory.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final StockRepository stockRepository;

    @GetMapping
    public List<StockItem> all() {
        return stockRepository.findAll();
    }

    @GetMapping("/{sku}")
    public StockItem one(@PathVariable String sku) {
        return stockRepository.findById(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));
    }
}
