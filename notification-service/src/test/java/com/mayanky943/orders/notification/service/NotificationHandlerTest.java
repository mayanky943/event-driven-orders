package com.mayanky943.orders.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.common.events.OrderCancelledEvent;
import com.mayanky943.orders.common.events.OrderConfirmedEvent;
import com.mayanky943.orders.notification.AbstractIntegrationTest;
import com.mayanky943.orders.notification.domain.Notification;
import com.mayanky943.orders.notification.domain.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationHandlerTest extends AbstractIntegrationTest {

    @Autowired private NotificationHandler handler;
    @Autowired private NotificationRepository repository;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void confirmedEventCreatesEmailNotification() throws Exception {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onConfirmed(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        List<Notification> notifs = repository.findByOrderId(event.getOrderId());
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(notifs.get(0).getChannel()).isEqualTo(Notification.Channel.EMAIL);
    }

    @Test
    void cancelledEventCreatesNotificationWithReason() throws Exception {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .reason("payment declined")
                .occurredAt(Instant.now())
                .build();

        tx.executeWithoutResult(s -> {
            try { handler.onCancelled(mapper.writeValueAsString(event)); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        List<Notification> notifs = repository.findByOrderId(event.getOrderId());
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getMessage()).contains("payment declined");
    }

    @Test
    void duplicateConfirmedEventIsIdempotent() throws Exception {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId("c-1")
                .occurredAt(Instant.now())
                .build();
        String payload = mapper.writeValueAsString(event);

        tx.executeWithoutResult(s -> {
            try { handler.onConfirmed(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        tx.executeWithoutResult(s -> {
            try { handler.onConfirmed(payload); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        assertThat(repository.findByOrderId(event.getOrderId())).hasSize(1);
    }
}
