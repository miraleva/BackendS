package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.entity.Notification;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.NotificationRepository;
import com.santsg.tourvisio.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        List<Map<String, Object>> result = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(notification -> Map.<String, Object>of(
                        "id", notification.getId(),
                        "title", notification.getTitle(),
                        "message", notification.getMessage(),
                        "type", notification.getType(),
                        "isRead", notification.getIsRead(),
                        "createdAt", notification.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        long count = notificationRepository
                .countByUserIdAndIsReadFalse(userId);

        return ResponseEntity.ok(
                Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long notificationId) {
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        Notification notification = notificationRepository
                .findById(notificationId)
                .orElse(null);

        if (notification == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Notification not found"));
        }

        if (notification.getUser() == null ||
                !notification.getUser().getId().equals(userId)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Forbidden"));
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);

        return ResponseEntity.ok(
                Map.of("success", true));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        notifications.forEach(
                notification -> notification.setIsRead(true));

        notificationRepository.saveAll(notifications);

        return ResponseEntity.ok(
                Map.of("success", true));
    }

    @PostMapping("/test")
    public ResponseEntity<?> createTestNotification(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        User user = userRepository
                .findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        if (!user.getNotifyInApp()) {
            return ResponseEntity.ok(
                    Map.of(
                            "created", false,
                            "message", "In-app notifications are disabled"));
        }

        Notification notification = Notification.builder()
                .user(user)
                .title("Test bildirimi")
                .message("Bildirim sistemi başarıyla çalışıyor.")
                .type("SYSTEM")
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        return ResponseEntity.ok(
                Map.of(
                        "created", true,
                        "id", saved.getId()));
    }
}