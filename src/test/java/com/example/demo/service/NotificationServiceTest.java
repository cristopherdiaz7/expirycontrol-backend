package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.NotificationsResponse;
import com.example.demo.exception.InvalidClientDateException;
import com.example.demo.exception.NotificationNotFoundException;
import com.example.demo.exception.ProductNotFoundException;
import com.example.demo.model.NotificationCategory;
import com.example.demo.model.NotificationRead;
import com.example.demo.model.NotificationSeverity;
import com.example.demo.model.Product;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private NotificationReadRepository notificationReadRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final LocalDate today = LocalDate.now();

    // --- Categorías ---

    @Test
    void categoryBoundariesFollowDaysRemaining() {
        assertEquals(Optional.of(NotificationCategory.EXPIRED), NotificationCategory.fromDaysRemaining(-30));
        assertEquals(Optional.of(NotificationCategory.EXPIRED), NotificationCategory.fromDaysRemaining(-1));
        assertEquals(Optional.of(NotificationCategory.EXPIRES_TODAY), NotificationCategory.fromDaysRemaining(0));
        assertEquals(Optional.of(NotificationCategory.WITHIN_3_DAYS), NotificationCategory.fromDaysRemaining(1));
        assertEquals(Optional.of(NotificationCategory.WITHIN_3_DAYS), NotificationCategory.fromDaysRemaining(3));
        assertEquals(Optional.of(NotificationCategory.WITHIN_7_DAYS), NotificationCategory.fromDaysRemaining(4));
        assertEquals(Optional.of(NotificationCategory.WITHIN_7_DAYS), NotificationCategory.fromDaysRemaining(7));
        assertEquals(Optional.of(NotificationCategory.WITHIN_14_DAYS), NotificationCategory.fromDaysRemaining(8));
        assertEquals(Optional.of(NotificationCategory.WITHIN_14_DAYS), NotificationCategory.fromDaysRemaining(14));
        assertEquals(Optional.empty(), NotificationCategory.fromDaysRemaining(15));
    }

    @Test
    void severityIsExpiredForExpiredAndTodayAndUpcomingForTheRest() {
        assertEquals(NotificationSeverity.EXPIRED, NotificationCategory.EXPIRED.getSeverity());
        assertEquals(NotificationSeverity.EXPIRED, NotificationCategory.EXPIRES_TODAY.getSeverity());
        assertEquals(NotificationSeverity.UPCOMING, NotificationCategory.WITHIN_3_DAYS.getSeverity());
        assertEquals(NotificationSeverity.UPCOMING, NotificationCategory.WITHIN_7_DAYS.getSeverity());
        assertEquals(NotificationSeverity.UPCOMING, NotificationCategory.WITHIN_14_DAYS.getSeverity());
    }

    @Test
    void eachProductAppearsOnceInItsOwnCategoryAndFarProductsAreLeftOut() {
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(
                product(1L, "Lejano", 15),
                product(2L, "Catorce", 14),
                product(3L, "Siete", 7),
                product(4L, "Tres", 3),
                product(5L, "Hoy", 0),
                product(6L, "Vencido", -2)));
        when(notificationReadRepository.findByUserId(USER_ID)).thenReturn(List.of());

        NotificationsResponse response = notificationService.getNotifications(USER_ID, null);

        List<NotificationResponse> notifications = response.getNotifications();
        assertEquals(5, notifications.size());
        assertEquals(5, notifications.stream().map(NotificationResponse::getProductId).distinct().count());
        assertEquals(5, response.getUnreadCount());

        // Ordenadas de más urgente a menos urgente.
        assertEquals(List.of("Vencido", "Hoy", "Tres", "Siete", "Catorce"),
                notifications.stream().map(NotificationResponse::getProductName).toList());
        assertEquals(List.of(NotificationCategory.EXPIRED, NotificationCategory.EXPIRES_TODAY, NotificationCategory.WITHIN_3_DAYS,
                        NotificationCategory.WITHIN_7_DAYS, NotificationCategory.WITHIN_14_DAYS),
                notifications.stream().map(NotificationResponse::getCategory).toList());
        assertEquals(List.of(-2L, 0L, 3L, 7L, 14L),
                notifications.stream().map(NotificationResponse::getDaysRemaining).toList());
        assertEquals(NotificationSeverity.EXPIRED, notifications.get(0).getSeverity());
        assertEquals(NotificationSeverity.UPCOMING, notifications.get(2).getSeverity());
    }

    @Test
    void clientDateChangesTheCategory() {
        // Para el servidor vence hoy; para un cliente que todavía está en ayer, vence en 1 día.
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(product(1L, "Leche", 0)));
        when(notificationReadRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertEquals(NotificationCategory.EXPIRES_TODAY,
                notificationService.getNotifications(USER_ID, null).getNotifications().get(0).getCategory());
        assertEquals(NotificationCategory.WITHIN_3_DAYS,
                notificationService.getNotifications(USER_ID, today.minusDays(1)).getNotifications().get(0).getCategory());
    }

    @Test
    void clientDateOutOfRangeIsRejected() {
        assertThrows(InvalidClientDateException.class,
                () -> notificationService.getNotifications(USER_ID, today.plusDays(3)));
    }

    // --- Estado de lectura ---

    @Test
    void notificationIsReadOnlyWhenCategoryAndDateMatch() {
        Product product = product(1L, "Leche", 2);
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(product));

        when(notificationReadRepository.findByUserId(USER_ID))
                .thenReturn(List.of(read(product, NotificationCategory.WITHIN_3_DAYS, product.getExpirationDate())));
        NotificationsResponse matching = notificationService.getNotifications(USER_ID, null);
        assertTrue(matching.getNotifications().get(0).isRead());
        assertEquals(0, matching.getUnreadCount());

        // Se había leído cuando estaba en "7 días": al pasar a "3 días" vuelve a estar sin leer.
        when(notificationReadRepository.findByUserId(USER_ID))
                .thenReturn(List.of(read(product, NotificationCategory.WITHIN_7_DAYS, product.getExpirationDate())));
        NotificationsResponse otherCategory = notificationService.getNotifications(USER_ID, null);
        assertFalse(otherCategory.getNotifications().get(0).isRead());
        assertEquals(1, otherCategory.getUnreadCount());

        // Misma categoría pero la fecha del producto cambió.
        when(notificationReadRepository.findByUserId(USER_ID))
                .thenReturn(List.of(read(product, NotificationCategory.WITHIN_3_DAYS, product.getExpirationDate().minusDays(1))));
        assertFalse(notificationService.getNotifications(USER_ID, null).getNotifications().get(0).isRead());
    }

    @Test
    void markAsReadStoresCurrentCategoryAndDate() {
        Product product = product(1L, "Leche", 5);
        when(productRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(product));
        when(notificationReadRepository.findByUserIdAndProductId(USER_ID, 1L)).thenReturn(Optional.empty());
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(product));
        when(notificationReadRepository.findByUserId(USER_ID)).thenReturn(List.of());

        notificationService.markAsRead(USER_ID, 1L, null);

        ArgumentCaptor<NotificationRead> captor = ArgumentCaptor.forClass(NotificationRead.class);
        verify(notificationReadRepository).save(captor.capture());
        assertEquals(NotificationCategory.WITHIN_7_DAYS, captor.getValue().getCategory());
        assertEquals(product.getExpirationDate(), captor.getValue().getExpirationDate());
        assertEquals(USER_ID, captor.getValue().getUser().getId());
        assertEquals(1L, captor.getValue().getProduct().getId());
    }

    @Test
    void markAsReadUpdatesTheExistingRowInsteadOfDuplicating() {
        Product product = product(1L, "Leche", 2);
        NotificationRead existing = read(product, NotificationCategory.WITHIN_7_DAYS, product.getExpirationDate());
        existing.setId(99L);
        when(productRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(product));
        when(notificationReadRepository.findByUserIdAndProductId(USER_ID, 1L)).thenReturn(Optional.of(existing));
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(product));
        when(notificationReadRepository.findByUserId(USER_ID)).thenReturn(List.of(existing));

        NotificationsResponse response = notificationService.markAsRead(USER_ID, 1L, null);

        ArgumentCaptor<NotificationRead> captor = ArgumentCaptor.forClass(NotificationRead.class);
        verify(notificationReadRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getId());
        assertEquals(NotificationCategory.WITHIN_3_DAYS, captor.getValue().getCategory());
        assertEquals(0, response.getUnreadCount());
    }

    @Test
    void markAsReadFailsForProductOfAnotherUser() {
        when(productRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> notificationService.markAsRead(USER_ID, 1L, null));
        verify(notificationReadRepository, never()).save(any());
    }

    @Test
    void markAsReadFailsWhenProductHasNoNotification() {
        when(productRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(product(1L, "Arroz", 60)));

        assertThrows(NotificationNotFoundException.class, () -> notificationService.markAsRead(USER_ID, 1L, null));
        verify(notificationReadRepository, never()).save(any());
    }

    @Test
    void markAllAsReadSavesOnlyUnreadNotifications() {
        Product alreadyRead = product(1L, "Leche", 2);
        Product unread = product(2L, "Yogur", -1);
        Product changedCategory = product(3L, "Queso", 6);
        Product withoutNotification = product(4L, "Arroz", 60);
        when(productRepository.findByUserId(USER_ID)).thenReturn(List.of(alreadyRead, unread, changedCategory, withoutNotification));
        when(notificationReadRepository.findByUserId(USER_ID)).thenReturn(List.of(
                read(alreadyRead, NotificationCategory.WITHIN_3_DAYS, alreadyRead.getExpirationDate()),
                read(changedCategory, NotificationCategory.WITHIN_14_DAYS, changedCategory.getExpirationDate())));

        notificationService.markAllAsRead(USER_ID, null);

        ArgumentCaptor<NotificationRead> captor = ArgumentCaptor.forClass(NotificationRead.class);
        verify(notificationReadRepository, times(2)).save(captor.capture());
        assertEquals(List.of(2L, 3L), captor.getAllValues().stream().map(read -> read.getProduct().getId()).toList());
        assertEquals(List.of(NotificationCategory.EXPIRED, NotificationCategory.WITHIN_7_DAYS),
                captor.getAllValues().stream().map(NotificationRead::getCategory).toList());
    }

    private Product product(Long id, String name, int daysFromToday) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Prueba")
                .category("Varios")
                .quantity(1)
                .expirationDate(today.plusDays(daysFromToday))
                .build();
    }

    private NotificationRead read(Product product, NotificationCategory category, LocalDate expirationDate) {
        return NotificationRead.builder()
                .product(product)
                .category(category)
                .expirationDate(expirationDate)
                .readAt(Instant.now())
                .build();
    }
}
