package neobank.application.usecase.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.User;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteUserAccountUseCase {

    private final UserRepository userRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional
    public void execute(UUID userId, String password) {
        log.info("Deleting account for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        cognitoAdapter.deleteUser(user.getEmail(), password);

        userRepository.delete(user);

        log.info("Account deleted successfully for user: {}", userId);
    }
}