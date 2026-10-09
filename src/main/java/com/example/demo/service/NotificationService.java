package com.example.demo.service;

import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.NotificationsResponse;
import com.example.demo.exception.NotificationNotFoundException;
import com.example.demo.exception.ProductNotFoundException;
import com.example.demo.model.NotificationCategory;
import com.example.demo.model.NotificationRead;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Las notificaciones no se guardan: se calculan a partir de los productos del usuario.
// Solo se guarda cuál fue la última que el usuario marcó como leída para cada producto.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProductRepository productRepository;
    private final NotificationReadRepository notificationReadRepository;

    @Transactional(readOnly = true)
    public NotificationsResponse getNotifications(Long userId, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);
        return buildResponse(userId, today);
    }

    @Transactional
    public NotificationsResponse markAsRead(Long userId, Long productId, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);
        Product product = productRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado"));
        NotificationCategory category = categoryOf(product, today)
                .orElseThrow(() -> new NotificationNotFoundException("El producto no tiene notificaciones"));

        Optional<NotificationRead> existing = notificationReadRepository.findByUserIdAndProductId(userId, productId);
        saveRead(userId, product, category, existing.orElse(null));

        return buildResponse(userId, today);
    }

    @Transactional
    public NotificationsResponse markAllAsRead(Long userId, LocalDate clientToday) {
        LocalDate today = ClientDates.resolveToday(clientToday);
        Map<Long, NotificationRead> readsByProduct = readsByProduct(userId);

        for (Product product : productRepository.findByUserId(userId)) {
            Optional<NotificationCategory> category = categoryOf(product, today);
            NotificationRead existing = readsByProduct.get(product.getId());
            if (category.isPresent() && !matches(existing, product, category.get())) {
                saveRead(userId, product, category.get(), existing);
            }
        }

        return buildResponse(userId, today);
    }

    private NotificationsResponse buildResponse(Long userId, LocalDate today) {
        Map<Long, NotificationRead> readsByProduct = readsByProduct(userId);

        List<NotificationResponse> notifications = productRepository.findByUserId(userId).stream()
                .map(product -> toNotification(product, today, readsByProduct.get(product.getId())))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(NotificationResponse::getExpirationDate)
                        .thenComparing(NotificationResponse::getProductName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long unreadCount = notifications.stream().filter(notification -> !notification.isRead()).count();

        return NotificationsResponse.builder()
                .unreadCount(unreadCount)
                .notifications(notifications)
                .build();
    }

    private Optional<NotificationResponse> toNotification(Product product, LocalDate today, NotificationRead read) {
        return categoryOf(product, today).map(category -> NotificationResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .category(category)
                .severity(category.getSeverity())
                .expirationDate(product.getExpirationDate())
                .daysRemaining(daysRemaining(product, today))
                .read(matches(read, product, category))
                .build());
    }

    private Optional<NotificationCategory> categoryOf(Product product, LocalDate today) {
        return NotificationCategory.fromDaysRemaining(daysRemaining(product, today));
    }

    private long daysRemaining(Product product, LocalDate today) {
        return ChronoUnit.DAYS.between(today, product.getExpirationDate());
    }

    // Leída solo si lo que se marcó coincide con la notificación actual:
    // un cambio de categoría o de fecha la deja otra vez sin leer.
    private boolean matches(NotificationRead read, Product product, NotificationCategory category) {
        return read != null
                && read.getCategory() == category
                && read.getExpirationDate().equals(product.getExpirationDate());
    }

    private Map<Long, NotificationRead> readsByProduct(Long userId) {
        return notificationReadRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(read -> read.getProduct().getId(), Function.identity(), (first, second) -> first));
    }

    private void saveRead(Long userId, Product product, NotificationCategory category, NotificationRead existing) {
        NotificationRead read = existing != null ? existing : NotificationRead.builder()
                .user(User.builder().id(userId).build())
                .product(product)
                .build();
        read.setCategory(category);
        read.setExpirationDate(product.getExpirationDate());
        read.setReadAt(Instant.now());
        notificationReadRepository.save(read);
    }
}
