package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.application.usecase.notification.*;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    private final DeleteNotificationUseCase deleteNotificationUseCase;
    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                             @RequestParam(defaultValue = "1") int page,
                                                                             @RequestParam(defaultValue = "20") int limit,
                                                                             @RequestParam(defaultValue = "false") boolean unreadOnly) {
        log.info("Get notifications request for user: {}", userPrincipal.getId());

        GetNotificationsUseCase.Result result = getNotificationsUseCase.execute(userPrincipal.getId(), page, limit, unreadOnly);
        Page<?> resultPage = result.page();

        Map<String, Object> response = Map.of(
                "notifications", resultPage.getContent(),
                "unread_count", result.unreadCount(),
                "pagination", Map.of(
                        "current_page", page,
                        "total_pages", resultPage.getTotalPages(),
                        "total_count", resultPage.getTotalElements()
                )
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                          @PathVariable UUID notificationId) {
        markNotificationReadUseCase.execute(notificationId, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<String>> markAllAsRead(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        markAllNotificationsReadUseCase.execute(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<String>> deleteNotification(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                  @PathVariable UUID notificationId) {
        deleteNotificationUseCase.execute(notificationId, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification deleted"));
    }

    @PostMapping("/register-device")
    public ResponseEntity<ApiResponse<String>> registerDevice(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @RequestBody Map<String, String> request) {
        String deviceToken = request.get("device_token");
        String platform = request.get("platform");

        registerDeviceTokenUseCase.execute(userPrincipal.getId(), deviceToken, platform);

        return ResponseEntity.ok(ApiResponse.success("Device registered for push notifications"));
    }
}
