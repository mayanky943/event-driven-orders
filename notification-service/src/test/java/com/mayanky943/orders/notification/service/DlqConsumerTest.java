package com.mayanky943.orders.notification.service;

import com.mayanky943.orders.notification.AbstractIntegrationTest;
import com.mayanky943.orders.notification.domain.PoisonMessage;
import com.mayanky943.orders.notification.domain.PoisonMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DlqConsumerTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private PoisonMessageRepository repository;

    @Test
    void messageSentToDlqTopicIsPersisted() {
        kafkaTemplate.send("test.topic.dlq", "k-1", "{\"broken\":true}");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(repository.findAll()).anySatisfy(p -> {
                assertThat(p.getOriginalTopic()).isEqualTo("test.topic");
                assertThat(p.getPayload()).contains("broken");
            });
        });
    }

    @Test
    void dlqConsumerStripsSuffixFromOriginalTopic() {
        kafkaTemplate.send("orders.created.dlq", "k-2", "{\"x\":1}");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(repository.findAll())
                    .anyMatch(p -> "orders.created".equals(p.getOriginalTopic()));
        });
    }
}
