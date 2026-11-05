package com.artsyvibe.controller;

import com.artsyvibe.model.Notification;
import com.artsyvibe.model.User;
import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Notifications of the signed-in user. The {userId} path segment is kept for compatibility with the
 * existing front end, but it must be the caller's own id.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping("/{userId}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(self(userId)));
    }

    @GetMapping("/{userId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(self(userId)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(currentUser.require(), id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all/{userId}")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long userId) {
        notificationService.markAllAsRead(self(userId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(currentUser.require(), id);
        return ResponseEntity.ok().build();
    }

    private User self(Long userId) {
        User me = currentUser.require();
        if (!me.getUserId().equals(userId)) {
            throw CurrentUser.forbidden("You can only access your own notifications.");
        }
        return me;
    }
}
