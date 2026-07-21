package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.User;
import neobank.domain.enums.KycStatus;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The gap this fills: every auth USE CASE already has thorough unit test
// coverage (LoginUseCaseTest, SignupUseCaseTest, etc.), but nothing tested
// the actual HTTP layer - request mapping, @Valid triggering 400s, and
// critically, which endpoints under /api/auth/** are really public. Writing
// this surfaced a real bug: SecurityConfig blanket-permitAll()'d the whole
// "/api/auth/**" tree, which also covered logout/change-password even
// though both require an authenticated principal internally - an
// unauthenticated call to either NPE'd/threw into a 500 instead of a proper
// 401/403. Fixed in SecurityConfig.java; these tests assert the corrected
// behavior. Same Testcontainers + @MockBean-the-AWS-adapters pattern as the
// existing AccountControllerIT, for consistency.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // Real network calls to Cognito/S3/SES are never available in CI/local
    // test runs - same rationale as AccountControllerIT.
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
                .cognitoUserId("cognito-auth-it")
                .email("auth-it@neobank.mx")
                .fullName("Auth IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ── public endpoints stay public ───────────────────────────────────

    @Test
    void login_validCredentials_noTokenRequired_returns200WithTokens() throws Exception {
        when(cognitoAdapter.login("auth-it@neobank.mx", "StrongPass1!"))
                .thenReturn(Map.of("accessToken", "access-123", "refreshToken", "refresh-123"));
        when(cognitoAdapter.getUserIdFromToken("access-123")).thenReturn("cognito-auth-it");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("email", "auth-it@neobank.mx");
                            put("password", "StrongPass1!");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-123"));
    }

    @Test
    void login_missingPassword_returns400ValidationErrorNot500() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("email", "auth-it@neobank.mx");
                        }})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void login_malformedEmail_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("email", "not-an-email");
                            put("password", "StrongPass1!");
                        }})))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_cognitoRejectsCredentials_returns401NotAGeneric500() throws Exception {
        when(cognitoAdapter.login(anyString(), anyString()))
                .thenThrow(new neobank.infrastructure.exception.UnauthorizedException(
                        "INVALID_CREDENTIALS", "Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("email", "auth-it@neobank.mx");
                            put("password", "wrong-password");
                        }})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_noToken_stillReachesTheUseCase() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("email", "auth-it@neobank.mx");
                        }})))
                .andExpect(status().isOk());
    }

    // ── logout/change-password require authentication (the fixed bug) ──

    @Test
    void logout_noAuthorizationHeaderAtAll_returns403NotA500() throws Exception {
        // Before the SecurityConfig fix, this NPE'd inside logout() on a null
        // @AuthenticationPrincipal and fell through to the generic 500 handler -
        // Spring Security's filter chain now rejects it before the controller
        // method is even invoked.
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_noAuthorizationHeaderAtAll_returns403NotA500() throws Exception {
        // Before the fix: @RequestHeader("Authorization") being required but
        // absent threw MissingRequestHeaderException, uncaught by any specific
        // handler, falling through to the generic 500 handler.
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType("application/json")
                        .content(json(new HashMap<>() {{
                            put("currentPassword", "OldPass1!");
                            put("newPassword", "NewPass1!");
                        }})))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_authenticated_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(authenticatedAs(user)))
                        .header("Authorization", "Bearer access-123"))
                .andExpect(status().isOk());
    }
}
