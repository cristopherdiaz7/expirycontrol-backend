package com.example.demo.dto;

import com.example.demo.model.NotificationCategory;
import com.example.demo.model.NotificationSeverity;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long productId;
    private String productName;
    private NotificationCategory category;
    private NotificationSeverity severity;
    private LocalDate expirationDate;
    private long daysRemaining;
    private boolean read;
}
