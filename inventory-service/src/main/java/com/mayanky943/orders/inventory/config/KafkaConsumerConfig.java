package com.mayanky943.orders.inventory.config;

import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.inventory.service.ReservationException;
import com.mayanky943.orders.inventory.service.ReservationFailureEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final ReservationFailureEmitter failureEmitter;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    /**
     * Three retry attempts with 1s spacing, then route to <topic>.dlq.
     * For ReservationException, emit the saga failure event before DLQing
     * so the order can proceed to CANCELLED.
     */
    private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        KafkaTopics.dlqOf(record.topic()), record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (consumerRecord, ex) -> {
                    handleReservationFailure(consumerRecord, ex);
                    recoverer.accept(consumerRecord, ex);
                },
                new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(ReservationException.class);
        return handler;
    }

    private void handleReservationFailure(ConsumerRecord<?, ?> record, Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && !(root instanceof ReservationException)) {
            root = root.getCause();
        }
        if (root instanceof ReservationException re) {
            failureEmitter.emit(re.getOrderId(), re.getMessage());
        } else {
            log.error("Unhandled failure on topic {}: {}", record.topic(), ex.getMessage());
        }
    }
}
