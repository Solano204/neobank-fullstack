package neobank.application.usecase.notification;

import neobank.domain.entity.Notification;
import neobank.domain.repository.NotificationRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteNotificationUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private DeleteNotificationUseCase deleteNotificationUseCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    void deletesAnOwnedNotification() {
        Notification notification = Notification.builder().id(notificationId).build();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));

        deleteNotificationUseCase.execute(notificationId, userId);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void throwsWhenNotificationDoesNotExistOrBelongsToAnotherUser() {
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteNotificationUseCase.execute(notificationId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
