package neobank.application.service;

import neobank.domain.entity.User;
import neobank.domain.entity.UserSession;
import neobank.domain.repository.UserSessionRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @InjectMocks
    private SecurityService securityService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
    }

    private UserSession sessionFor(User owner, LocalDateTime expiresAt) {
        return UserSession.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .deviceName("iPhone 15")
                .ipAddress("203.0.113.9")
                .expiresAt(expiresAt)
                .lastActiveAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    @Test
    void getActiveSessions_returnsOnlySessionsThatHaveNotExpired() {
        UserSession active = sessionFor(user, LocalDateTime.now().plusHours(1));
        UserSession expired = sessionFor(user, LocalDateTime.now().minusHours(1));
        when(userSessionRepository.findByUserId(userId)).thenReturn(List.of(active, expired));

        List<Map<String, Object>> sessions = securityService.getActiveSessions(userId);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("id")).isEqualTo(active.getId());
    }

    @Test
    void getActiveSessions_mapsDeviceAndIpFields() {
        UserSession active = sessionFor(user, LocalDateTime.now().plusHours(1));
        when(userSessionRepository.findByUserId(userId)).thenReturn(List.of(active));

        Map<String, Object> session = securityService.getActiveSessions(userId).get(0);

        assertThat(session.get("device")).isEqualTo("iPhone 15");
        assertThat(session.get("ip_address")).isEqualTo("203.0.113.9");
    }

    @Test
    void getActiveSessions_noSessions_returnsEmptyList() {
        when(userSessionRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(securityService.getActiveSessions(userId)).isEmpty();
    }

    @Test
    void terminateSession_ownedByCaller_deletesIt() {
        UserSession session = sessionFor(user, LocalDateTime.now().plusHours(1));
        when(userSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        securityService.terminateSession(session.getId(), userId);

        verify(userSessionRepository).delete(session);
    }

    @Test
    void terminateSession_unknownSessionId_throwsResourceNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityService.terminateSession(sessionId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userSessionRepository, never()).delete(any());
    }

    // The core IDOR check: a session that exists but belongs to a DIFFERENT
    // user must be rejected, not silently deleted.
    @Test
    void terminateSession_belongsToAnotherUser_throwsUnauthorizedAndDoesNotDeleteIt() {
        User someoneElse = User.builder().id(UUID.randomUUID()).build();
        UserSession othersSession = sessionFor(someoneElse, LocalDateTime.now().plusHours(1));
        when(userSessionRepository.findById(othersSession.getId())).thenReturn(Optional.of(othersSession));

        assertThatThrownBy(() -> securityService.terminateSession(othersSession.getId(), userId))
                .isInstanceOf(UnauthorizedException.class);
        verify(userSessionRepository, never()).delete(any());
    }

    @Test
    void terminateAllSessions_deletesEverySessionForThatUserOnly() {
        securityService.terminateAllSessions(userId);

        verify(userSessionRepository).deleteByUserId(userId);
    }
}
