package com.mayanky943.orders.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    /**
     * Polls the outbox table inside a transaction, sends each row to Kafka,
     * and marks it published on success. If Kafka fails the transaction
     * rolls back and the rows stay unpublished — they get retried on the
     * next tick.
     *
     * Lock is FOR UPDATE so multiple service instances don't double-publish.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = repository.findUnpublishedForUpdate(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Publishing {} outbox events", batch.size());

        for (OutboxEvent event : batch) {
            try {
                SendResult<String, String> result = kafkaTemplate.send(
                                event.getTopic(),
                                event.getAggregateId().toString(),
                                event.getPayload())
                        .get();
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                log.debug("Published {} to {} (offset={})",
                        event.getEventType(),
                        event.getTopic(),
                        result.getRecordMetadata().offset());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                log.error("Failed to publish outbox event {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
                // Don't rethrow — keep going on this batch; row stays unpublished
                // and gets retried next tick.
            }
        }
        repository.saveAll(batch);
    }
}
