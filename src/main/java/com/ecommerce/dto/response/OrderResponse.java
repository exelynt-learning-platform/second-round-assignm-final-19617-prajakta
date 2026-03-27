package com.ecommerce.dto.response;

import com.ecommerce.entity.Order;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class OrderResponse {
    private Long orderId;
    private Long userId;
    private String username;
    private List<OrderItemResponse> items;
    private BigDecimal totalPrice;
    private Order.OrderStatus status;
    private Order.PaymentStatus paymentStatus;
    private String stripeClientSecret;   // sent on creation only
    private ShippingDetails shippingDetails;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data @Builder
    public static class OrderItemResponse {
        private Long productId;
        private String productName;
        private String imageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    @Data @Builder
    public static class ShippingDetails {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String zip;
        private String country;
    }
}
