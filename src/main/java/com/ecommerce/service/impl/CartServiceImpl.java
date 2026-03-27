package com.ecommerce.service.impl;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.CartService;
import com.ecommerce.service.impl.ProductServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductServiceImpl productService;   // reuse active-product lookup

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = productService.findActiveProductById(request.getProductId());

        if (!product.isInStock(request.getQuantity())) {
            throw new BadRequestException(
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            product.getStockQuantity(), request.getQuantity()));
        }

        cart.findItemByProductId(product.getId())
                .ifPresentOrElse(existing -> {
                    int newQty = existing.getQuantity() + request.getQuantity();
                    if (!product.isInStock(newQty)) {
                        throw new BadRequestException("Cannot add more items – insufficient stock");
                    }
                    existing.setQuantity(newQty);
                }, () -> {
                    CartItem item = CartItem.builder()
                            .product(product)
                            .quantity(request.getQuantity())
                            .unitPrice(product.getPrice())
                            .build();
                    cart.addItem(item);
                });

        cartRepository.save(cart);
        log.debug("Added product {} to cart for user {}", product.getId(), userId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long productId, int quantity) {
        if (quantity < 1) throw new BadRequestException("Quantity must be at least 1");

        Cart cart = getOrCreateCart(userId);
        Product product = productService.findActiveProductById(productId);

        CartItem item = cart.findItemByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item not found in cart for product id: " + productId));

        if (!product.isInStock(quantity)) {
            throw new BadRequestException(
                    "Insufficient stock. Available: " + product.getStockQuantity());
        }

        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());  // refresh price
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cart.findItemByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item not found in cart for product id: " + productId));

        cart.removeItem(item);
        cartRepository.save(cart);
        log.debug("Removed product {} from cart for user {}", productId, userId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.clear();
        cartRepository.save(cart);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .imageUrl(item.getProduct().getImageUrl())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .availableStock(item.getProduct().getStockQuantity())
                        .build())
                .toList();

        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemResponses)
                .totalItems(cart.getTotalItems())
                .totalPrice(cart.getTotalPrice())
                .build();
    }
}
