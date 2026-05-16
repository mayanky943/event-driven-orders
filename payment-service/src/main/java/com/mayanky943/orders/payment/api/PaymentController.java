package com.mayanky943.orders.payment.api;

import com.mayanky943.orders.payment.domain.Payment;
import com.mayanky943.orders.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/order/{orderId}")
    public Payment forOrder(@PathVariable UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for order " + orderId));
    }
}
