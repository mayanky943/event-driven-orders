package com.mayanky943.orders.order.service;

import com.mayanky943.orders.common.idempotency.IdempotencyService;
import com.mayanky943.orders.order.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest extends AbstractIntegrationTest {

    @Autowired private IdempotencyService idempotency;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void cleanRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void firstAcquireSucceeds() {
        assertThat(idempotency.tryAcquire("k-" + UUID.randomUUID())).isTrue();
    }

    @Test
    void duplicateAcquireFails() {
        String key = "k-" + UUID.randomUUID();
        assertThat(idempotency.tryAcquire(key)).isTrue();
        assertThat(idempotency.tryAcquire(key)).isFalse();
    }

    @Test
    void releaseAllowsReacquire() {
        String key = "k-" + UUID.randomUUID();
        idempotency.tryAcquire(key);
        idempotency.release(key);
        assertThat(idempotency.tryAcquire(key)).isTrue();
    }

    @Test
    void isProcessedReflectsState() {
        String key = "k-" + UUID.randomUUID();
        assertThat(idempotency.isProcessed(key)).isFalse();
        idempotency.tryAcquire(key);
        assertThat(idempotency.isProcessed(key)).isTrue();
    }

    @Test
    void distinctKeysIndependent() {
        String k1 = "k-" + UUID.randomUUID();
        String k2 = "k-" + UUID.randomUUID();
        assertThat(idempotency.tryAcquire(k1)).isTrue();
        assertThat(idempotency.tryAcquire(k2)).isTrue();
    }
}
