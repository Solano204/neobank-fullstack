package neobank.presentation.controller;

import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.adapter.dynamodb.TransactionHistoryAdapter;
import neobank.infrastructure.adapter.dynamodb.TransactionRecord;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AnalyticsControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    // Real DynamoDB isn't provisioned for these IT tests (only Postgres via
    // Testcontainers) - mocked at the same adapter boundary as S3/Cognito/SES.
    @MockBean
    private TransactionHistoryAdapter transactionHistoryAdapter;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .cognitoUserId("cognito-analytics-it")
                .email("analytics-it@neobank.mx")
                .fullName("Analytics IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        accountRepository.save(Account.builder()
                .user(user).accountNumber("123412341234123412").accountType(AccountType.CHECKING)
                .balance(new BigDecimal("1000.00")).availableBalance(new BigDecimal("1000.00"))
                .currency("MXN").status(AccountStatus.ACTIVE).build());

        when(transactionHistoryAdapter.findForAccount(any(), anyLong())).thenReturn(List.of());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/spending")).andExpect(status().isForbidden());
    }

    @Test
    void getSpendingAnalytics_defaultsToMonthPeriod() throws Exception {
        mockMvc.perform(get("/api/analytics/spending").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());
    }

    @Test
    void getSpendingAnalytics_acceptsAnExplicitPeriod() throws Exception {
        mockMvc.perform(get("/api/analytics/spending").with(authentication(authenticatedAs(user))).param("period", "week"))
                .andExpect(status().isOk());
    }

    @Test
    void getBalanceForecast_noHistory_forecastsFlatFromCurrentBalance() throws Exception {
        mockMvc.perform(get("/api/analytics/balance-forecast").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_balance").value(1000.00))
                .andExpect(jsonPath("$.data.forecast_7_days").value(1000.00));
    }

    @Test
    void getBalanceForecast_withCompletedIncomingTransactions_projectsGrowth() throws Exception {
        List<TransactionRecord> incoming = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new TransactionRecord("tx" + i, System.currentTimeMillis(), "OTHER", "123412341234123412", new BigDecimal("50.00"), "COMPLETED", true))
                .toList();
        when(transactionHistoryAdapter.findForAccount(org.mockito.ArgumentMatchers.eq("123412341234123412"), anyLong())).thenReturn(incoming);

        mockMvc.perform(get("/api/analytics/balance-forecast").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast_30_days").value(org.hamcrest.Matchers.greaterThan(1000.0)));
    }
}
