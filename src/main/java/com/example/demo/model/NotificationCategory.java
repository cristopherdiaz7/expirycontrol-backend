package com.example.demo.model;

import java.util.Optional;

// Categorías del centro de notificaciones. Cada producto cae como máximo en una.
public enum NotificationCategory {

    EXPIRED(NotificationSeverity.EXPIRED),
    EXPIRES_TODAY(NotificationSeverity.EXPIRED),
    WITHIN_3_DAYS(NotificationSeverity.UPCOMING),
    WITHIN_7_DAYS(NotificationSeverity.UPCOMING),
    WITHIN_14_DAYS(NotificationSeverity.UPCOMING);

    private final NotificationSeverity severity;

    NotificationCategory(NotificationSeverity severity) {
        this.severity = severity;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    // daysRemaining: días entre hoy y la fecha de vencimiento (negativo si ya pasó).
    public static Optional<NotificationCategory> fromDaysRemaining(long daysRemaining) {
        if (daysRemaining < 0) {
            return Optional.of(EXPIRED);
        }
        if (daysRemaining == 0) {
            return Optional.of(EXPIRES_TODAY);
        }
        if (daysRemaining <= 3) {
            return Optional.of(WITHIN_3_DAYS);
        }
        if (daysRemaining <= 7) {
            return Optional.of(WITHIN_7_DAYS);
        }
        if (daysRemaining <= 14) {
            return Optional.of(WITHIN_14_DAYS);
        }
        return Optional.empty();
    }
}
