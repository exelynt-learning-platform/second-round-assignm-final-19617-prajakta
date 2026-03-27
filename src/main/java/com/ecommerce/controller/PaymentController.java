package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Stripe sends webhook events here.
     * Must be excluded from JWT auth and CSRF protection (see SecurityConfig).
     * Register this URL in your Stripe dashboard → Developers → Webhooks.
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            paymentService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok(ApiResponse.success("Webhook processed", "OK"));
        } catch (PaymentException e) {
            log.warn("Webhook processing failed: {}", e.getMessage());
            // Return 400 so Stripe retries
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
