package com.mayanky943.orders.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotBlank
    private String customerId;

    @NotEmpty
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotBlank
        private String sku;

        @Positive
        private int quantity;

        @Positive
        private BigDecimal unitPrice;
    }
}
