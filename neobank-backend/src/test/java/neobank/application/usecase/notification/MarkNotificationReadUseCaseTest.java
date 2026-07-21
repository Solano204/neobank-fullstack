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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkNotificationReadUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private MarkNotificationReadUseCase markNotificationReadUseCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    void marksAnOwnedNotificationAsRead() {
        Notification notification = Notification.builder().id(notificationId).read(false).build();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));

        markNotificationReadUseCase.execute(notificationId, userId);

        assertThat(notification.getRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void throwsWhenNotificationDoesNotExistOrBelongsToAnotherUser() {
        // findByIdAndUserId scopes by both id AND userId at the query level -
        // a notification belonging to someone else looks identical to "not
        // found" here, so this single query is also the IDOR guard.
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> markNotificationReadUseCase.execute(notificationId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
