package com.mayanky943.orders.common.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    UUID getOrderId();
    Instant getOccurredAt();
}
