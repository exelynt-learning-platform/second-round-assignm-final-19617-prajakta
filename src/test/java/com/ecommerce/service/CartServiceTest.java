package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.CartServiceImpl;
import com.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock UserRepository userRepository;
    @Mock ProductServiceImpl productService;
    @InjectMocks CartServiceImpl cartService;

    private User user;
    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("alice").email("alice@example.com").build();

        product = Product.builder()
                .id(10L)
                .name("Gadget")
                .price(new BigDecimal("19.99"))
                .stockQuantity(5)
                .isActive(true)
                .build();

        cart = Cart.builder().id(100L).user(user).build();
    }

    @Test
    @DisplayName("addItem: creates a new CartItem when product not already in cart")
    void addItem_newProduct_success() {
        CartItemRequest req = new CartItemRequest();
        req.setProductId(10L);
        req.setQuantity(2);

        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(productService.findActiveProductById(10L)).thenReturn(product);
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse result = cartService.addItem(1L, req);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("39.98");
    }

    @Test
    @DisplayName("addItem: rejects quantity exceeding available stock")
    void addItem_insufficientStock_throws() {
        CartItemRequest req = new CartItemRequest();
        req.setProductId(10L);
        req.setQuantity(10);  // Only 5 in stock

        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(productService.findActiveProductById(10L)).thenReturn(product);

        assertThatThrownBy(() -> cartService.addItem(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("removeItem: removes existing item from cart")
    void removeItem_success() {
        CartItem item = CartItem.builder()
                .id(200L).product(product).quantity(1).unitPrice(product.getPrice()).build();
        cart.addItem(item);

        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(productService.findActiveProductById(10L)).thenReturn(product);
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse result = cartService.removeItem(1L, 10L);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("updateItem: rejects quantity less than 1")
    void updateItem_zeroQuantity_throws() {
        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(productService.findActiveProductById(10L)).thenReturn(product);

        assertThatThrownBy(() -> cartService.updateItem(1L, 10L, 0))
                .isInstanceOf(BadRequestException.class);
    }
}
