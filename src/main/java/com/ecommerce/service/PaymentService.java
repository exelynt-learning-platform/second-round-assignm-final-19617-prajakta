package com.ecommerce.service;

import com.ecommerce.entity.User;

import java.math.BigDecimal;

public interface PaymentService {

    record PaymentIntentResult(String paymentIntentId, String clientSecret) {}

    PaymentIntentResult createPaymentIntent(Long orderId, BigDecimal amount, User user);

    void handleWebhookEvent(String payload, String sigHeader);
}
