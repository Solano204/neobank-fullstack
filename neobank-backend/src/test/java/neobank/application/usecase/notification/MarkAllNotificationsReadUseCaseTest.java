package neobank.application.usecase.notification;

import neobank.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarkAllNotificationsReadUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;

    @Test
    void delegatesToTheRepositorysBulkUpdateForThatUserOnly() {
        UUID userId = UUID.randomUUID();

        markAllNotificationsReadUseCase.execute(userId);

        verify(notificationRepository).markAllAsRead(userId);
    }
}
