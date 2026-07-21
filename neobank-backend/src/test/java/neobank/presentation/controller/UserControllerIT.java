package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.User;
import neobank.domain.entity.UserSettings;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.UserRepository;
import neobank.domain.repository.UserSettingsRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class UserControllerIT {

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
    private UserSettingsRepository userSettingsRepository;

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
                .cognitoUserId("cognito-user-it")
                .email("user-it@neobank.mx")
                .fullName("User IT")
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
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/profile")).andExpect(status().isForbidden());
    }

    @Test
    void getProfile_returnsTheAuthenticatedUsersOwnProfile() throws Exception {
        mockMvc.perform(get("/api/users/profile").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user-it@neobank.mx"))
                .andExpect(jsonPath("$.data.fullName").value("User IT"));
    }

    @Test
    void updateProfile_persistsTheNewNameAndPhone() throws Exception {
        mockMvc.perform(put("/api/users/profile")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("fullName", "Updated Name");
                            put("phone", "+525599999999");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getFullName()).isEqualTo("Updated Name");
        org.assertj.core.api.Assertions.assertThat(updated.getPhone()).isEqualTo("+525599999999");
    }

    @Test
    void updateProfile_malformedPhone_returns400ValidationErrorNot500() throws Exception {
        mockMvc.perform(put("/api/users/profile")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("phone", "not-a-phone-number");
                        }})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // UserSettings is a separate row provisioned at signup (SignupUseCase),
    // not lazily created on first read - a user built directly via
    // userRepository.save() (bypassing signup, as this whole file's fixture
    // does) genuinely has none yet, matching real never-signed-up-through-
    // the-real-flow state.
    @Test
    void getSettings_userWithNoSettingsRowYet_returns404NotA500() throws Exception {
        mockMvc.perform(get("/api/users/settings").with(authentication(authenticatedAs(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSettings_returnsThePersistedValues() throws Exception {
        userSettingsRepository.save(UserSettings.builder()
                .user(user).emailNotifications(true).pushNotifications(true).smsNotifications(false)
                .mfaEnabled(false).biometricEnabled(false).language("es").currency("MXN").theme("light")
                .build());

        mockMvc.perform(get("/api/users/settings").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferences.language").value("es"))
                .andExpect(jsonPath("$.data.notifications.email").value(true));
    }

    @Test
    void updateSettings_persistsThePreferences() throws Exception {
        userSettingsRepository.save(UserSettings.builder()
                .user(user).emailNotifications(true).pushNotifications(true).smsNotifications(false)
                .mfaEnabled(false).biometricEnabled(false).language("es").currency("MXN").theme("light")
                .build());

        mockMvc.perform(put("/api/users/settings")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content("{\"preferences\":{\"language\":\"en\",\"currency\":\"USD\",\"theme\":\"dark\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/settings").with(authentication(authenticatedAs(user))))
                .andExpect(jsonPath("$.data.preferences.language").value("en"))
                .andExpect(jsonPath("$.data.preferences.theme").value("dark"));
    }

    // The controller's success message promises "You have 30 days to cancel"
    // but DeleteUserAccountUseCase does an immediate hard delete - documented
    // as a real mismatch in TESTING-STRATEGY-ADDENDUM.md. This test asserts
    // the actual (immediate-delete) behavior, not the message's claim.
    @Test
    void deleteAccount_immediatelyRemovesTheUserAndRevokesCognito() throws Exception {
        mockMvc.perform(delete("/api/users/account")
                        .with(authentication(authenticatedAs(user)))
                        .param("password", "current-password"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(userRepository.findById(user.getId())).isEmpty();
        org.mockito.Mockito.verify(cognitoAdapter).deleteUser("user-it@neobank.mx", "current-password");
    }
}
