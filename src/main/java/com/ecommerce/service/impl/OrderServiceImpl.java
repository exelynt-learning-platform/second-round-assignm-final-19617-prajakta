package com.ecommerce.service.impl;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    @Override
    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new BadRequestException("Cart not found"));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place an order with an empty cart");
        }

        // Validate stock and compute total
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem ci : cart.getItems()) {
            Product p = ci.getProduct();
            if (!p.isInStock(ci.getQuantity())) {
                throw new BadRequestException(
                        String.format("'%s' only has %d unit(s) in stock (requested %d)",
                                p.getName(), p.getStockQuantity(), ci.getQuantity()));
            }
            total = total.add(ci.getSubtotal());
        }

        // Build order
        Order order = Order.builder()
                .user(user)
                .totalPrice(total)
                .status(Order.OrderStatus.PENDING)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .shippingAddressLine1(request.getAddressLine1())
                .shippingAddressLine2(request.getAddressLine2())
                .shippingCity(request.getCity())
                .shippingState(request.getState())
                .shippingZip(request.getZip())
                .shippingCountry(request.getCountry())
                .build();

        // Snapshot cart items → order items; deduct stock
        for (CartItem ci : cart.getItems()) {
            OrderItem oi = OrderItem.builder()
                    .order(order)
                    .product(ci.getProduct())
                    .productName(ci.getProduct().getName())
                    .quantity(ci.getQuantity())
                    .unitPrice(ci.getUnitPrice())
                    .build();
            order.getItems().add(oi);
            ci.getProduct().decreaseStock(ci.getQuantity());
            productRepository.save(ci.getProduct());
        }

        order = orderRepository.save(order);

        // Create Stripe PaymentIntent
        try {
            PaymentService.PaymentIntentResult pi =
                    paymentService.createPaymentIntent(order.getId(), total, user);
            order.setStripePaymentIntentId(pi.paymentIntentId());
            order.setStripeClientSecret(pi.clientSecret());
            order = orderRepository.save(order);
        } catch (Exception e) {
            log.error("Stripe PaymentIntent creation failed for order {}: {}", order.getId(), e.getMessage());
            // Order is saved – frontend can retry payment separately
        }

        // Clear cart
        cart.clear();
        cartRepository.save(cart);

        log.info("Order {} created for user {} – total {}", order.getId(), userId, total);
        return toResponse(order, true);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return toResponse(order, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(o -> toResponse(o, false));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() == Order.OrderStatus.SHIPPED ||
            order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel an order that has already been shipped");
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled");
        }

        // Restore stock
        order.getItems().forEach(oi -> {
            oi.getProduct().increaseStock(oi.getQuantity());
            productRepository.save(oi.getProduct());
        });

        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        log.info("Order {} cancelled by user {}", orderId, userId);
        return toResponse(order, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(o -> toResponse(o, false));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        try {
            order.setStatus(Order.OrderStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid order status: " + status);
        }
        return toResponse(orderRepository.save(order), false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private OrderResponse toResponse(Order order, boolean includeClientSecret) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(oi -> OrderResponse.OrderItemResponse.builder()
                        .productId(oi.getProduct().getId())
                        .productName(oi.getProductName())
                        .imageUrl(oi.getProduct().getImageUrl())
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .subtotal(oi.getSubtotal())
                        .build())
                .toList();

        OrderResponse.ShippingDetails shipping = OrderResponse.ShippingDetails.builder()
                .addressLine1(order.getShippingAddressLine1())
                .addressLine2(order.getShippingAddressLine2())
                .city(order.getShippingCity())
                .state(order.getShippingState())
                .zip(order.getShippingZip())
                .country(order.getShippingCountry())
                .build();

        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUser().getId())
                .username(order.getUser().getUsername())
                .items(items)
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .stripeClientSecret(includeClientSecret ? order.getStripeClientSecret() : null)
                .shippingDetails(shipping)
                .trackingNumber(order.getTrackingNumber())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
