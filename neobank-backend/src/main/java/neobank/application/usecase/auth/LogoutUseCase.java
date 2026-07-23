package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.repository.UserSessionRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogoutUseCase {

    private final UserSessionRepository userSessionRepository;
    private final CognitoAdapter cognitoAdapter;

    // Takes the userId (not a full User entity) - matches every other use
    // case in this codebase (e.g. FreezeAccountUseCase), and
    // UserSessionRepository already exposes deleteByUserId directly, so
    // there was never a need to load the whole User just to delete its
    // sessions. The controller used to pass a hardcoded `null` here, which
    // NPE'd on every real logout call (see AuthControllerIT).
    @Transactional
    public void execute(String accessToken, UUID userId) {
        log.info("Logging out user: {}", userId);

        cognitoAdapter.logout(accessToken);
        userSessionRepository.deleteByUserId(userId);

        log.info("User logged out successfully: {}", userId);
    }
}