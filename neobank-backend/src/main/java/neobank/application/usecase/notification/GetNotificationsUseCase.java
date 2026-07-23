package neobank.application.usecase.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.NotificationResponse;
import neobank.application.usecase.mapper.NotificationMapper;
import neobank.domain.entity.Notification;
import neobank.domain.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public record Result(Page<NotificationResponse> page, long unreadCount) {}

    @Transactional(readOnly = true)
    public Result execute(UUID userId, int page, int limit, boolean unreadOnly) {
        log.info("Fetching notifications for user: {} (page={}, limit={}, unreadOnly={})", userId, page, limit, unreadOnly);

        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageRequest)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);

        return new Result(notifications.map(notificationMapper::toResponse), unreadCount);
    }
}
