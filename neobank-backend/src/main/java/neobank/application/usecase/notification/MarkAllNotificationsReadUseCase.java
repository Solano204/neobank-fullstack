package neobank.application.usecase.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkAllNotificationsReadUseCase {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void execute(UUID userId) {
        notificationRepository.markAllAsRead(userId);
        log.info("All notifications marked as read for user {}", userId);
    }
}
