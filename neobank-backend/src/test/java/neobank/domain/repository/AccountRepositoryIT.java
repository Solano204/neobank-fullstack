package neobank.domain.repository;

import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class AccountRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private AccountRepository accountRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .cognitoUserId("cognito-" + email)
                .email(email)
                .fullName("Test User")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    @Test
    void savesAndFindsAccountByAccountNumber() {
        User user = persistUser("owner@neobank.mx");
        Account account = Account.builder()
                .user(user)
                .accountNumber("111122223333444455")
                .accountType(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(account);

        Optional<Account> found = accountRepository.findByAccountNumber("111122223333444455");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getEmail()).isEqualTo("owner@neobank.mx");
    }

    @Test
    void existsByAccountNumberReflectsPersistedState() {
        assertThat(accountRepository.existsByAccountNumber("999900001111222233")).isFalse();

        User user = persistUser("owner2@neobank.mx");
        accountRepository.save(Account.builder()
                .user(user)
                .accountNumber("999900001111222233")
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .build());

        assertThat(accountRepository.existsByAccountNumber("999900001111222233")).isTrue();
    }

    @Test
    void findByIdAndUserIdOnlyMatchesOwner() {
        User owner = persistUser("owner3@neobank.mx");
        User stranger = persistUser("stranger@neobank.mx");
        Account account = accountRepository.save(Account.builder()
                .user(owner)
                .accountNumber("555566667777888899")
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .build());

        assertThat(accountRepository.findByIdAndUserId(account.getId(), owner.getId())).isPresent();
        assertThat(accountRepository.findByIdAndUserId(account.getId(), stranger.getId())).isEmpty();
    }
}
