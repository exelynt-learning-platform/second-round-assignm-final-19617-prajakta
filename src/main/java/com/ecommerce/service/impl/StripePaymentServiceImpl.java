package com.ecommerce.service.impl;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Override
    public PaymentIntentResult createPaymentIntent(Long orderId, BigDecimal amount, User user) {
        try {
            String customerId = getOrCreateStripeCustomer(user);

            // Stripe works in smallest currency unit (cents)
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", String.valueOf(orderId));
            metadata.put("userId", String.valueOf(user.getId()));

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setCustomer(customerId)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .putAllMetadata(metadata)
                    .setDescription("Order #" + orderId)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Created Stripe PaymentIntent {} for order {}", intent.getId(), orderId);
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret());

        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent: {}", e.getMessage());
            throw new PaymentException("Failed to initialise payment", e);
        }
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new PaymentException("Invalid Stripe webhook signature");
        }

        log.info("Received Stripe webhook event: {}", event.getType());

        Optional<StripeObject> stripeObject = event.getDataObjectDeserializer()
                .getObject();

        switch (event.getType()) {
            case "payment_intent.succeeded" -> stripeObject
                    .filter(o -> o instanceof PaymentIntent)
                    .map(o -> (PaymentIntent) o)
                    .ifPresent(this::handlePaymentSucceeded);

            case "payment_intent.payment_failed" -> stripeObject
                    .filter(o -> o instanceof PaymentIntent)
                    .map(o -> (PaymentIntent) o)
                    .ifPresent(this::handlePaymentFailed);

            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ─── Private webhook handlers ─────────────────────────────────────────────

    private void handlePaymentSucceeded(PaymentIntent intent) {
        String orderId = intent.getMetadata().get("orderId");
        if (orderId == null) return;

        orderRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(order -> {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("Payment succeeded for order {}. Status → CONFIRMED", orderId);
        });
    }

    private void handlePaymentFailed(PaymentIntent intent) {
        String orderId = intent.getMetadata().get("orderId");
        if (orderId == null) return;

        orderRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(order -> {
            order.setPaymentStatus(Order.PaymentStatus.FAILED);
            orderRepository.save(order);
            log.warn("Payment FAILED for order {}", orderId);
        });
    }

    // ─── Customer helper ──────────────────────────────────────────────────────

    private String getOrCreateStripeCustomer(User user) throws StripeException {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(fullName(user))
                .putMetadata("userId", String.valueOf(user.getId()))
                .build();
        Customer customer = Customer.create(params);
        // Persist asynchronously – best effort; order creation proceeds regardless
        user.setStripeCustomerId(customer.getId());
        return customer.getId();
    }

    private String fullName(User user) {
        if (user.getFirstName() == null && user.getLastName() == null) return user.getUsername();
        return (user.getFirstName() == null ? "" : user.getFirstName()).trim() + " " +
               (user.getLastName() == null ? "" : user.getLastName()).trim();
    }
}
