package neobank.application.usecase.auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.SignupRequest;
import neobank.application.dto.response.SignupResponse;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.entity.UserSettings;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignupUseCase {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional
    public SignupResponse execute(SignupRequest request) {
        log.info("Starting signup process for email: {}", request.getEmail());

        validateEmailNotExists(request.getEmail());

        String cognitoUserId = cognitoAdapter.signUp(
                request.getEmail(),
                request.getPassword(),
                request.getFullName()
        );

        User user = createUser(request, cognitoUserId);
        User savedUser = userRepository.save(user);

        Account account = createAccount(savedUser);
        Account savedAccount = accountRepository.save(account);

        createDefaultSettings(savedUser);

        log.info("User created successfully with ID: {}", savedUser.getId());

        return SignupResponse.builder()
                .message("User created. Check email for verification code.")
                .userId(savedUser.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .status("VERIFICATION_PENDING")
                .build();
    }

    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "This email is already registered");
        }
    }

    private User createUser(SignupRequest request, String cognitoUserId) {
        return User.builder()
                .cognitoUserId(cognitoUserId)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .curp(request.getCurp())
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build();
    }

    private Account createAccount(User user) {
        return Account.builder()
                .user(user)
                .accountNumber(generateAccountNumber())
                .accountType(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .overdraftLimit(BigDecimal.ZERO)
                .build();
    }

    private void createDefaultSettings(User user) {
        UserSettings settings = UserSettings.builder()
                .user(user)
                .emailNotifications(true)
                .pushNotifications(true)
                .smsNotifications(false)
                .mfaEnabled(false)
                .biometricEnabled(false)
                .language("es-MX")
                .currency("MXN")
                .theme("light")
                .build();
        userSettingsRepository.save(settings);
    }

    private String generateAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder accountNumber = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            accountNumber.append(random.nextInt(10));
        }

        if (accountRepository.existsByAccountNumber(accountNumber.toString())) {
            return generateAccountNumber();
        }

        return accountNumber.toString();
    }
}