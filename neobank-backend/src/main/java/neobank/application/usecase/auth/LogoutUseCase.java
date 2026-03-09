package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.User;
import neobank.domain.repository.UserSessionRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogoutUseCase {

    private final UserSessionRepository userSessionRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional
    public void execute(String accessToken, User user) {
        log.info("Logging out user: {}", user.getId());

        cognitoAdapter.logout(accessToken);
        userSessionRepository.deleteByUser(user);

        log.info("User logged out successfully: {}", user.getId());
    }
}