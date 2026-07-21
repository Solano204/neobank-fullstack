package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.SupportTicketRepository;
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
import java.util.HashMap;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class SupportControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .cognitoUserId("cognito-support-it")
                .email("support-it@neobank.mx")
                .fullName("Support IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void rejectsChatRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/support/chat").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    // Not actually public - /api/support/faq isn't in SecurityConfig's
    // permitAll() list, so (unlike its @GetMapping with no @AuthenticationPrincipal
    // param might suggest) it still requires authentication like every other
    // endpoint not explicitly allow-listed.
    @Test
    void faq_requiresAuthenticationLikeEveryOtherEndpoint() throws Exception {
        mockMvc.perform(get("/api/support/faq")).andExpect(status().isForbidden());
    }

    @Test
    void faq_authenticated_returnsCategories() throws Exception {
        mockMvc.perform(get("/api/support/faq").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories", hasSize(1)));
    }

    @Test
    void chat_balanceQuestion_answersWithTheRealBalance() throws Exception {
        accountRepository.save(Account.builder()
                .user(user).accountNumber("123412341234123412").accountType(AccountType.CHECKING)
                .balance(new BigDecimal("250.00")).availableBalance(new BigDecimal("250.00"))
                .currency("MXN").status(AccountStatus.ACTIVE).build());

        mockMvc.perform(post("/api/support/chat")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("message", "What's my balance?");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("CheckBalance"))
                .andExpect(jsonPath("$.data.bot_response", org.hamcrest.Matchers.containsString("250.00")));
    }

    @Test
    void chat_unrecognizedMessage_fallsBackGracefully() throws Exception {
        mockMvc.perform(post("/api/support/chat")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("message", "asdkjfhaskjdfh");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FallbackIntent"));
    }

    @Test
    void createTicket_persistsAndDefaultsPriorityToLowWhenInvalid() throws Exception {
        mockMvc.perform(post("/api/support/ticket")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("subject", "Card declined");
                            put("description", "My card was declined at checkout");
                            put("priority", "URGENT-NOT-A-REAL-PRIORITY");
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subject").value("Card declined"))
                .andExpect(jsonPath("$.data.priority").value("LOW"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        org.assertj.core.api.Assertions.assertThat(supportTicketRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
    }

    @Test
    void getTickets_returnsOnlyTheCallersTickets() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger-support")
                .email("stranger-support@neobank.mx")
                .fullName("Stranger")
                .phone("+525500000000")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/support/ticket")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("subject", "My ticket");
                            put("description", "d");
                        }})));
        mockMvc.perform(post("/api/support/ticket")
                        .with(authentication(authenticatedAs(stranger)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("subject", "Stranger ticket");
                            put("description", "d");
                        }})));

        mockMvc.perform(get("/api/support/tickets").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tickets", hasSize(1)))
                .andExpect(jsonPath("$.data.tickets[0].subject").value("My ticket"));
    }
}
