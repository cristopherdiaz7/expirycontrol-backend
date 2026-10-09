package com.example.demo.service;

import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.dto.ProductStatsResponse;
import com.example.demo.exception.ProductNotFoundException;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final LossService lossService;
    private final TransactionTemplate transactionTemplate;
    private final UserLocks userLocks;

    @Transactional
    public ProductResponse create(Long userId, ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory().trim())
                .quantity(request.getQuantity())
                .expirationDate(request.getExpirationDate())
                .unitPrice(request.getUnitPrice())
                .user(User.builder().id(userId).build())
                .build();

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    public List<ProductResponse> getAll(Long userId) {
        return productRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> getExpired(Long userId, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);
        return productRepository.findByUserId(userId).stream()
                .filter(product -> !product.getExpirationDate().isAfter(today))
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> getExpiringSoon(Long userId, int days, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);
        LocalDate thresholdDate = today.plusDays(Math.max(days, 0));

        return productRepository.findByUserId(userId).stream()
                .filter(product -> product.getExpirationDate().isAfter(today))
                .filter(product -> !product.getExpirationDate().isAfter(thresholdDate))
                .map(this::mapToResponse)
                .toList();
    }

    public ProductStatsResponse getStats(Long userId, int days, LocalDate clientToday) {
        List<Product> products = productRepository.findByUserId(userId);
        LocalDate today = ClientDates.resolveToday(clientToday);
        LocalDate thresholdDate = today.plusDays(Math.max(days, 0));

        long expiredProducts = products.stream()
                .filter(product -> product.getExpirationDate().isBefore(today) || product.getExpirationDate().isEqual(today))
                .count();

        long expiringSoonProducts = products.stream()
                .filter(product -> product.getExpirationDate().isAfter(today))
                .filter(product -> !product.getExpirationDate().isAfter(thresholdDate))
                .count();

        long totalProducts = products.size();
        long validProducts = totalProducts - expiredProducts - expiringSoonProducts;

        return ProductStatsResponse.builder()
                .totalProducts(totalProducts)
                .expiredProducts(expiredProducts)
                .expiringSoonProducts(expiringSoonProducts)
                .validProducts(validProducts)
                .build();
    }

    public ProductResponse getById(Long userId, Long id) {
        Product product = productRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado"));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse update(Long userId, Long id, ProductRequest request) {
        Product product = productRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado"));

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription().trim());
        product.setCategory(request.getCategory().trim());
        product.setQuantity(request.getQuantity());
        product.setExpirationDate(request.getExpirationDate());
        product.setUnitPrice(request.getUnitPrice());

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    // Usa el candado del usuario y su propia transacción para no cruzarse con el registro de pérdidas.
    public void delete(Long userId, Long id, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);

        synchronized (userLocks.forUser(userId)) {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado"));
                // Si estaba vencido, su pérdida queda en el historial.
                lossService.freezeBeforeProductDeletion(product, today);
                // El estado de lectura de sus notificaciones se va con el producto.
                notificationReadRepository.deleteByProductId(product.getId());
                productRepository.delete(product);
            });
        }
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .quantity(product.getQuantity())
                .expirationDate(product.getExpirationDate())
                .unitPrice(product.getUnitPrice())
                .build();
    }
}
