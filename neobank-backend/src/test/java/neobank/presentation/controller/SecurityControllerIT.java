package neobank.presentation.controller;

import neobank.domain.entity.User;
import neobank.domain.entity.UserSession;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.UserRepository;
import neobank.domain.repository.UserSessionRepository;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The gap this fills: SecurityController (session management) had zero
// HTTP-level coverage. Writing it surfaced a real bug: SecurityService threw
// bare RuntimeException for "session not found" / "not your session", which
// GlobalExceptionHandler has no specific handler for, so both fell through
// to the generic 500 handler instead of 404/403 - the exact same bug class
// already found and fixed on AuthController's logout/change-password (see
// AuthControllerIT). Fixed in SecurityService.java to throw the existing
// ResourceNotFoundException/UnauthorizedException domain exceptions instead.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class SecurityControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    private User user;
    private UserSession session;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .cognitoUserId("cognito-security-it")
                .email("security-it@neobank.mx")
                .fullName("Security IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        session = userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionToken("session-token-" + UUID.randomUUID())
                .deviceName("iPhone 15")
                .ipAddress("203.0.113.9")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .lastActiveAt(LocalDateTime.now())
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/security/sessions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsActiveSessionsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/security/sessions").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions", hasSize(1)))
                .andExpect(jsonPath("$.data.sessions[0].device").value("iPhone 15"));
    }

    @Test
    void doesNotListAnotherUsersSessions() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger-sec")
                .email("stranger-sec@neobank.mx")
                .fullName("Stranger")
                .phone("+525500000000")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        mockMvc.perform(get("/api/security/sessions").with(authentication(authenticatedAs(stranger))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions", hasSize(0)));
    }

    @Test
    void terminateSession_ownSession_returns200AndActuallyDeletesIt() throws Exception {
        mockMvc.perform(delete("/api/security/sessions/{id}", session.getId())
                        .with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(userSessionRepository.findById(session.getId())).isEmpty();
    }

    @Test
    void terminateSession_unknownId_returns404NotA500() throws Exception {
        mockMvc.perform(delete("/api/security/sessions/{id}", UUID.randomUUID())
                        .with(authentication(authenticatedAs(user))))
                .andExpect(status().isNotFound());
    }

    // Direct regression test for the bug found while writing this file.
    @Test
    void terminateSession_belongingToAnotherUser_returns401NotA500_andIsNotDeleted() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger-sec-2")
                .email("stranger-sec-2@neobank.mx")
                .fullName("Stranger Two")
                .phone("+525500000001")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        mockMvc.perform(delete("/api/security/sessions/{id}", session.getId())
                        .with(authentication(authenticatedAs(stranger))))
                .andExpect(status().isUnauthorized());

        org.assertj.core.api.Assertions.assertThat(userSessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    void terminateAllSessions_returns200AndClearsEveryOwnSession() throws Exception {
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionToken("session-token-" + UUID.randomUUID())
                .deviceName("Desktop Chrome")
                .ipAddress("203.0.113.10")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .lastActiveAt(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/security/sessions/all").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(userSessionRepository.findByUserId(user.getId())).isEmpty();
    }
}
