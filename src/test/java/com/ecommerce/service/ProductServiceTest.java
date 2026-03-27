package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
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
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @InjectMocks ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Test Widget")
                .description("A fine widget")
                .price(new BigDecimal("29.99"))
                .stockQuantity(100)
                .imageUrl("https://example.com/widget.png")
                .category("Widgets")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("createProduct: persists and maps entity correctly")
    void createProduct_success() {
        ProductRequest req = new ProductRequest();
        req.setName("Test Widget");
        req.setDescription("A fine widget");
        req.setPrice(new BigDecimal("29.99"));
        req.setStockQuantity(100);
        req.setCategory("Widgets");

        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse result = productService.createProduct(req);

        assertThat(result.getName()).isEqualTo("Test Widget");
        assertThat(result.getPrice()).isEqualByComparingTo("29.99");
        assertThat(result.isInStock()).isTrue();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("getProductById: throws 404 for unknown id")
    void getProductById_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    @DisplayName("getProductById: returns response for active product")
    void getProductById_found() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse result = productService.getProductById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Widget");
    }

    @Test
    @DisplayName("deleteProduct: soft-deletes by setting isActive=false")
    void deleteProduct_softDelete() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any())).thenReturn(sampleProduct);

        productService.deleteProduct(1L);

        assertThat(sampleProduct.getIsActive()).isFalse();
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("updateProduct: applies all field changes")
    void updateProduct_success() {
        ProductRequest req = new ProductRequest();
        req.setName("Updated Widget");
        req.setDescription("Even better");
        req.setPrice(new BigDecimal("39.99"));
        req.setStockQuantity(50);
        req.setCategory("Premium");

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = productService.updateProduct(1L, req);

        assertThat(result.getName()).isEqualTo("Updated Widget");
        assertThat(result.getPrice()).isEqualByComparingTo("39.99");
    }
}
