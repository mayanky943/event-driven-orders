package com.mayanky943.orders.order.service;

import com.mayanky943.orders.common.kafka.KafkaTopics;
import com.mayanky943.orders.common.outbox.OutboxEvent;
import com.mayanky943.orders.common.outbox.OutboxRepository;
import com.mayanky943.orders.order.AbstractIntegrationTest;
import com.mayanky943.orders.order.api.CreateOrderRequest;
import com.mayanky943.orders.order.domain.Order;
import com.mayanky943.orders.order.domain.OrderRepository;
import com.mayanky943.orders.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest extends AbstractIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;

    @Test
    void createPersistsOrderInPendingState() {
        Order order = orderService.create(sampleRequest("cust-1", "SKU-A", 2, "9.99"));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCustomerId()).isEqualTo("cust-1");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("19.98");
    }

    @Test
    void createComputesTotalFromLines() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("c");
        CreateOrderRequest.Line l1 = new CreateOrderRequest.Line();
        l1.setSku("A"); l1.setQuantity(3); l1.setUnitPrice(new BigDecimal("10.00"));
        CreateOrderRequest.Line l2 = new CreateOrderRequest.Line();
        l2.setSku("B"); l2.setQuantity(1); l2.setUnitPrice(new BigDecimal("4.50"));
        req.setLines(List.of(l1, l2));

        Order order = orderService.create(req);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("34.50");
    }

    @Test
    void createPersistsOutboxEventInSameTransaction() {
        Order order = orderService.create(sampleRequest("c", "SKU-A", 1, "5.00"));

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getAggregateId()).isEqualTo(order.getId());
            assertThat(e.getTopic()).isEqualTo(KafkaTopics.ORDERS_CREATED);
            assertThat(e.getEventType()).isEqualTo("OrderCreatedEvent");
            assertThat(e.isPublished()).isFalse();
        });
    }

    @Test
    void findByIdReturnsOrder() {
        Order saved = orderService.create(sampleRequest("c", "SKU-A", 1, "5.00"));
        Order found = orderService.findById(saved.getId());
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        assertThatThrownBy(() -> orderService.findById(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistsLines() {
        Order order = orderService.create(sampleRequest("c", "SKU-A", 2, "5.00"));
        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getLines()).hasSize(1);
        assertThat(reloaded.getLines().get(0).getSku()).isEqualTo("SKU-A");
        assertThat(reloaded.getLines().get(0).getQuantity()).isEqualTo(2);
    }

    private CreateOrderRequest sampleRequest(String customer, String sku, int qty, String price) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customer);
        CreateOrderRequest.Line line = new CreateOrderRequest.Line();
        line.setSku(sku);
        line.setQuantity(qty);
        line.setUnitPrice(new BigDecimal(price));
        req.setLines(List.of(line));
        return req;
    }
}
