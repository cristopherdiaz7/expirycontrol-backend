package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.dto.ProductStatsResponse;
import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductShouldPersistAndReturnResponse() {
        ProductRequest request = new ProductRequest(
                "Leche",
                "Entera",
                "Lácteos",
                12,
                LocalDate.now().plusDays(10)
        );

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Leche")
                .description("Entera")
                .category("Lácteos")
                .quantity(12)
                .expirationDate(LocalDate.now().plusDays(10))
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.create(10L, request);

        assertNotNull(response);
        assertEquals("Leche", response.getName());
        assertEquals("Lácteos", response.getCategory());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void getAllProductsShouldReturnListOfResponses() {
        Product product = Product.builder()
                .id(2L)
                .name("Yogur")
                .description("Natural")
                .category("Lácteos")
                .quantity(5)
                .expirationDate(LocalDate.now().plusDays(7))
                .build();

        when(productRepository.findByUserId(10L)).thenReturn(List.of(product));

        List<ProductResponse> response = productService.getAll(10L);

        assertEquals(1, response.size());
        assertEquals("Yogur", response.get(0).getName());
    }

    @Test
    void updateProductShouldModifyExistingProduct() {
        Product existing = Product.builder()
                .id(3L)
                .name("Queso")
                .description("Mozzarella")
                .category("Lácteos")
                .quantity(4)
                .expirationDate(LocalDate.now().plusDays(5))
                .build();

        ProductRequest request = new ProductRequest(
                "Queso cheddar",
                "Sin grasa",
                "Lácteos",
                8,
                LocalDate.now().plusDays(15)
        );

        when(productRepository.findByIdAndUserId(3L, 10L)).thenReturn(java.util.Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(10L, 3L, request);

        assertEquals("Queso cheddar", response.getName());
        assertEquals(8, response.getQuantity());
    }

    @Test
    void getExpiredProductsShouldReturnOnlyExpiredProducts() {
        Product expired = Product.builder()
                .id(4L)
                .name("Yogur")
                .description("Natural")
                .category("Lácteos")
                .quantity(2)
                .expirationDate(LocalDate.now().minusDays(1))
                .build();

        Product fresh = Product.builder()
                .id(5L)
                .name("Manteca")
                .description("Sin sal")
                .category("Lácteos")
                .quantity(3)
                .expirationDate(LocalDate.now().plusDays(20))
                .build();

        when(productRepository.findByUserId(10L)).thenReturn(List.of(expired, fresh));

        List<ProductResponse> response = productService.getExpired(10L);

        assertEquals(1, response.size());
        assertEquals("Yogur", response.get(0).getName());
    }

    @Test
    void getExpiringSoonProductsShouldReturnOnlyProductsWithinThreshold() {
        Product nearExpiry = Product.builder()
                .id(6L)
                .name("Leche")
                .description("Entera")
                .category("Lácteos")
                .quantity(4)
                .expirationDate(LocalDate.now().plusDays(3))
                .build();

        Product later = Product.builder()
                .id(7L)
                .name("Pan")
                .description("Integral")
                .category("Panadería")
                .quantity(10)
                .expirationDate(LocalDate.now().plusDays(30))
                .build();

        when(productRepository.findByUserId(10L)).thenReturn(List.of(nearExpiry, later));

        List<ProductResponse> response = productService.getExpiringSoon(10L, 7);

        assertEquals(1, response.size());
        assertEquals("Leche", response.get(0).getName());
    }

    @Test
    void getStatsShouldReturnCountsForUserProducts() {
        Product expired = Product.builder()
                .id(8L)
                .name("Queso")
                .description("Azul")
                .category("Lácteos")
                .quantity(1)
                .expirationDate(LocalDate.now().minusDays(2))
                .build();

        Product expiringSoon = Product.builder()
                .id(9L)
                .name("Jugo")
                .description("Naranja")
                .category("Bebidas")
                .quantity(2)
                .expirationDate(LocalDate.now().plusDays(5))
                .build();

        Product valid = Product.builder()
                .id(10L)
                .name("Arroz")
                .description("Integral")
                .category("Cereales")
                .quantity(10)
                .expirationDate(LocalDate.now().plusDays(50))
                .build();

        when(productRepository.findByUserId(10L)).thenReturn(List.of(expired, expiringSoon, valid));

        ProductStatsResponse stats = productService.getStats(10L, 7);

        assertEquals(3, stats.getTotalProducts());
        assertEquals(1, stats.getExpiredProducts());
        assertEquals(1, stats.getExpiringSoonProducts());
        assertEquals(1, stats.getValidProducts());
    }
}
