package com.mayanky943.orders.notification.api;

import com.mayanky943.orders.notification.domain.Notification;
import com.mayanky943.orders.notification.domain.NotificationRepository;
import com.mayanky943.orders.notification.domain.PoisonMessage;
import com.mayanky943.orders.notification.domain.PoisonMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notifications;
    private final PoisonMessageRepository poison;

    @GetMapping("/order/{orderId}")
    public List<Notification> forOrder(@PathVariable UUID orderId) {
        return notifications.findByOrderId(orderId);
    }

    @GetMapping("/dlq")
    public List<PoisonMessage> dlq() {
        return poison.findAll();
    }
}
