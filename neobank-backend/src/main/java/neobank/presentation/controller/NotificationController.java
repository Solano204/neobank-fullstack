package neobank.presentation.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                             @RequestParam(defaultValue = "1") int page,
                                                                             @RequestParam(defaultValue = "20") int limit,
                                                                             @RequestParam(defaultValue = "false") boolean unreadOnly) {
        log.info("Get notifications request for user: {}", userPrincipal.getId());

        List<Map<String, Object>> notifications = new ArrayList<>();

        Map<String, Object> response = new HashMap<>();
        response.put("notifications", notifications);
        response.put("unread_count", 0);
        response.put("pagination", Map.of(
                "current_page", page,
                "total_pages", 1,
                "total_count", 0
        ));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                          @PathVariable UUID notificationId) {
        log.info("Mark notification as read: {} for user: {}", notificationId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<String>> markAllAsRead(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Mark all notifications as read for user: {}", userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<String>> deleteNotification(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                  @PathVariable UUID notificationId) {
        log.info("Delete notification: {} for user: {}", notificationId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("Notification deleted"));
    }

    @PostMapping("/register-device")
    public ResponseEntity<ApiResponse<String>> registerDevice(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @RequestBody Map<String, String> request) {
        log.info("Register device for user: {}", userPrincipal.getId());

        String deviceToken = request.get("device_token");
        String platform = request.get("platform");

        return ResponseEntity.ok(ApiResponse.success("Device registered for push notifications"));
    }
}