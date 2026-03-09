package neobank.application.usecase.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.LoginRequest;
import neobank.application.dto.response.LoginResponse;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginUseCase {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        Map<String, String> tokens = cognitoAdapter.login(
                request.getEmail(),
                request.getPassword()
        );

        String cognitoUserId = cognitoAdapter.getUserIdFromToken(tokens.get("accessToken"));

        User user = userRepository.findByCognitoUserId(cognitoUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Account> accounts = accountRepository.findByUser(user);
        String accountNumber = accounts.isEmpty() ? null : accounts.get(0).getAccountNumber();

        log.info("User logged in successfully: {}", user.getId());

        return LoginResponse.builder()
                .accessToken(tokens.get("accessToken"))
                .refreshToken(tokens.get("refreshToken"))
                .expiresIn(3600)
                .user(LoginResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .kycStatus(user.getKycStatus())
                        .accountNumber(accountNumber)
                        .createdAt(user.getCreatedAt())
                        .build())
                .build();
    }
}