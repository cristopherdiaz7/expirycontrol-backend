package com.example.demo.controller;

import com.example.demo.dto.NotificationsResponse;
import com.example.demo.model.User;
import com.example.demo.service.NotificationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationsResponse> getAll(@AuthenticationPrincipal User user,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(notificationService.getNotifications(user.getId(), today));
    }

    @PostMapping("/{productId}/read")
    public ResponseEntity<NotificationsResponse> markAsRead(@AuthenticationPrincipal User user,
                                                            @PathVariable Long productId,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(notificationService.markAsRead(user.getId(), productId, today));
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationsResponse> markAllAsRead(@AuthenticationPrincipal User user,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(notificationService.markAllAsRead(user.getId(), today));
    }
}
