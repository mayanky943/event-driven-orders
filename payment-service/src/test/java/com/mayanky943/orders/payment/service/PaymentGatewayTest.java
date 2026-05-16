package com.mayanky943.orders.payment.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayTest {

    @Test
    void zeroFailureRateAlwaysSucceeds() {
        PaymentGateway gw = new PaymentGateway();
        ReflectionTestUtils.setField(gw, "failureRate", 0.0);
        for (int i = 0; i < 50; i++) {
            assertThat(gw.charge(UUID.randomUUID(), BigDecimal.TEN).success()).isTrue();
        }
    }

    @Test
    void fullFailureRateAlwaysFails() {
        PaymentGateway gw = new PaymentGateway();
        ReflectionTestUtils.setField(gw, "failureRate", 1.0);
        for (int i = 0; i < 50; i++) {
            PaymentGateway.Result r = gw.charge(UUID.randomUUID(), BigDecimal.TEN);
            assertThat(r.success()).isFalse();
            assertThat(r.failureReason()).isNotBlank();
        }
    }

    @Test
    void resultCarriesReasonOnFailure() {
        PaymentGateway gw = new PaymentGateway();
        ReflectionTestUtils.setField(gw, "failureRate", 1.0);
        PaymentGateway.Result r = gw.charge(UUID.randomUUID(), BigDecimal.ONE);
        assertThat(r.success()).isFalse();
        assertThat(r.failureReason()).isEqualTo("Card declined (simulated)");
    }
}
