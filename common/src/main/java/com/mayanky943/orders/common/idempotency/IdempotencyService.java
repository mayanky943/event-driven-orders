package com.mayanky943.orders.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed deduplication. Each consumer wraps event handling with
 * tryAcquire(key) — if the key was set in the last {@code defaultTtl}
 * window, the event is treated as already processed.
 *
 * SETNX gives us atomic "set if not exists" — no race between check + set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "idem:";

    private final StringRedisTemplate redis;

    @Value("${idempotency.ttl-hours:24}")
    private long ttlHours;

    public boolean tryAcquire(String key) {
        String redisKey = PREFIX + key;
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(redisKey, "1", Duration.ofHours(ttlHours));
        boolean ok = Boolean.TRUE.equals(acquired);
        if (!ok) {
            log.debug("Duplicate detected for key {}", key);
        }
        return ok;
    }

    public void release(String key) {
        redis.delete(PREFIX + key);
    }

    public boolean isProcessed(String key) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + key));
    }
}
