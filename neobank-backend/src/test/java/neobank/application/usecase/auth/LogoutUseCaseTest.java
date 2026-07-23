package neobank.application.usecase.auth;

import neobank.domain.repository.UserSessionRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private LogoutUseCase logoutUseCase;

    // Takes userId directly (not a full User entity) - the controller only
    // ever has a UserPrincipal (id/email/etc, not a hydrated User), and
    // UserSessionRepository already exposes deleteByUserId, so there's no
    // reason to require a full entity just to delete its sessions.
    @Test
    void revokesCognitoSessionAndDeletesLocalSessionsByUserId() {
        UUID userId = UUID.randomUUID();

        logoutUseCase.execute("access-token-123", userId);

        verify(cognitoAdapter).logout("access-token-123");
        verify(userSessionRepository).deleteByUserId(userId);
    }

    @Test
    void revokesCognitoSessionBeforeDeletingLocalSessions() {
        UUID userId = UUID.randomUUID();

        logoutUseCase.execute("access-token-123", userId);

        InOrder order = inOrder(cognitoAdapter, userSessionRepository);
        order.verify(cognitoAdapter).logout("access-token-123");
        order.verify(userSessionRepository).deleteByUserId(userId);
    }

    @Test
    void doesNotTouchAnyOtherUsersSessions() {
        UUID userId = UUID.randomUUID();

        logoutUseCase.execute("access-token-123", userId);

        verify(userSessionRepository).deleteByUserId(userId);
        verifyNoMoreInteractions(userSessionRepository);
    }
}
