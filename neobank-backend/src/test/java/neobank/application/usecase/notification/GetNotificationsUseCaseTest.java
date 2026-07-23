package neobank.application.usecase.notification;

import neobank.application.dto.response.NotificationResponse;
import neobank.application.usecase.mapper.NotificationMapper;
import neobank.domain.entity.Notification;
import neobank.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetNotificationsUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private GetNotificationsUseCase getNotificationsUseCase;

    private final UUID userId = UUID.randomUUID();

    @Test
    void unreadOnlyFalse_queriesAllNotificationsOrderedByNewestFirst() {
        Notification n = Notification.builder().id(UUID.randomUUID()).build();
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class))).thenReturn(page);
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(3L);
        when(notificationMapper.toResponse(n)).thenReturn(NotificationResponse.builder().id(n.getId()).build());

        GetNotificationsUseCase.Result result = getNotificationsUseCase.execute(userId, 1, 20, false);

        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.unreadCount()).isEqualTo(3L);
        verify(notificationRepository, org.mockito.Mockito.never()).findByUserIdAndReadFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void unreadOnlyTrue_queriesOnlyUnreadNotifications() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(0L);

        getNotificationsUseCase.execute(userId, 1, 20, true);

        verify(notificationRepository).findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(userId), any(Pageable.class));
        verify(notificationRepository, org.mockito.Mockito.never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void page1_translatesToPageIndex0ForSpringDataPagination() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(0L);

        getNotificationsUseCase.execute(userId, 1, 20, false);

        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Test
    void page0OrNegative_clampsToPageIndex0RatherThanUnderflowing() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(0L);

        getNotificationsUseCase.execute(userId, 0, 20, false);

        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
