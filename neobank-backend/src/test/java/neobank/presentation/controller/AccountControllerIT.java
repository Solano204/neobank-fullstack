package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.adapter.ses.SesAdapter;
import neobank.infrastructure.security.JwtTokenProvider;
import neobank.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AccountControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    // Real credentials/network calls to AWS Cognito, S3 and SES are never available in CI/local
    // test runs, so the external adapters are stubbed instead of wired to real AWS clients.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .cognitoUserId("cognito-account-it")
                .email("account-it@neobank.mx")
                .fullName("Account IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        account = accountRepository.save(Account.builder()
                .user(user)
                .accountNumber("123412341234123412")
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("500.00"))
                .availableBalance(new BigDecimal("500.00"))
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void listsAccountsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/accounts").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountNumber").value("123412341234123412"));
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        // No custom AuthenticationEntryPoint is configured, so Spring Security's
        // default falls back to 403 rather than 401 for missing credentials.
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void freezeThenUnfreezeRoundTripsThroughRealDatabase() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/freeze", account.getId())
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                            put("reason", "suspicious activity");
                        }})))
                .andExpect(status().isOk());

        Account frozen = accountRepository.findById(account.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(frozen.getStatus()).isEqualTo(AccountStatus.FROZEN);

        mockMvc.perform(post("/api/accounts/{id}/unfreeze", account.getId())
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content("{\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());

        Account unfrozen = accountRepository.findById(account.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unfrozen.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void returnsNotFoundForAccountOwnedByAnotherUser() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger")
                .email("stranger@neobank.mx")
                .fullName("Stranger")
                .phone("+525500000000")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        mockMvc.perform(get("/api/accounts/{id}", account.getId())
                        .with(authentication(authenticatedAs(stranger))))
                .andExpect(status().isNotFound());
    }
}
