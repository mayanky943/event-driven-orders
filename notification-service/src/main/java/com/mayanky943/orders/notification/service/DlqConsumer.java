package com.mayanky943.orders.notification.service;

import com.mayanky943.orders.notification.domain.PoisonMessage;
import com.mayanky943.orders.notification.domain.PoisonMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Listens to every *.dlq topic. Poison messages are persisted so an
 * operator can inspect / replay them; in a real system you'd add an
 * alert or a manual replay endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {

    private final PoisonMessageRepository repository;

    @KafkaListener(topicPattern = ".*\\.dlq", groupId = "notification-service-dlq")
    public void consume(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String reason = headerAsString(record, "kafka_dlt-exception-message");

        PoisonMessage poison = PoisonMessage.builder()
                .id(UUID.randomUUID())
                .originalTopic(topic.replace(".dlq", ""))
                .partition(record.partition())
                .offsetValue(record.offset())
                .messageKey(record.key())
                .payload(record.value())
                .exceptionMessage(reason)
                .receivedAt(Instant.now())
                .build();
        repository.save(poison);

        log.error("DLQ message captured: topic={} key={} reason={}",
                topic, record.key(), reason);
    }

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
