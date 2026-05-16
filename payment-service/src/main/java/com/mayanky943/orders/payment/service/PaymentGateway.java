package com.mayanky943.orders.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a real payment processor. Fails a configurable fraction
 * of charges so the saga's compensating path actually gets exercised
 * in load tests.
 */
@Component
public class PaymentGateway {

    @Value("${payment.failure-rate:0.1}")
    private double failureRate;

    public Result charge(UUID orderId, BigDecimal amount) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return new Result(false, "Card declined (simulated)");
        }
        return new Result(true, null);
    }

    public record Result(boolean success, String failureReason) {}
}
