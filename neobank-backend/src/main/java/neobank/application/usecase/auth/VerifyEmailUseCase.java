package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.VerifyEmailRequest;
import neobank.application.dto.response.VerifyEmailResponse;
import neobank.domain.entity.User;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerifyEmailUseCase {

    private final UserRepository userRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional
    public VerifyEmailResponse execute(VerifyEmailRequest request) {
        log.info("Verifying email for: {}", request.getEmail());

        cognitoAdapter.confirmSignUp(request.getEmail(), request.getCode());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("Email verified successfully for user: {}", user.getId());

        return VerifyEmailResponse.builder()
                .message("Email verified. Please login to continue.")
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(null)
                .user(VerifyEmailResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .kycStatus(user.getKycStatus())
                        .build())
                .build();
    }
}