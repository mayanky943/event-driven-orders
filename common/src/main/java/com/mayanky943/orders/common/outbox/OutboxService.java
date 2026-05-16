package com.mayanky943.orders.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Persists an event into the outbox table in the SAME transaction as the
     * business write. This is the "outbox" half of the transactional outbox
     * pattern — the polling publisher emits to Kafka asynchronously.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String aggregateType, UUID aggregateId, String topic, DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent record = OutboxEvent.builder()
                    .id(event.getEventId() == null ? UUID.randomUUID() : event.getEventId())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(event.getClass().getSimpleName())
                    .topic(topic)
                    .payload(payload)
                    .published(false)
                    .createdAt(Instant.now())
                    .attempts(0)
                    .build();
            return repository.save(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event " + event, e);
        }
    }
}
