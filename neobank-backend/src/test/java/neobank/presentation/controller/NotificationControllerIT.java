package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.Notification;
import neobank.domain.entity.User;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.DeviceTokenRepository;
import neobank.domain.repository.NotificationRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class NotificationControllerIT {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

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
                .cognitoUserId("cognito-notif-it")
                .email("notif-it@neobank.mx")
                .fullName("Notif IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    private Notification notificationFor(User owner, boolean read) {
        return notificationRepository.save(Notification.builder()
                .user(owner)
                .title("Transfer received")
                .message("You received $100.00 MXN")
                .type("TRANSACTION")
                .read(read)
                .build());
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
    }

    @Test
    void listsNotificationsWithPaginationAndUnreadCount() throws Exception {
        notificationFor(user, false);
        notificationFor(user, true);

        mockMvc.perform(get("/api/notifications").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications", hasSize(2)))
                .andExpect(jsonPath("$.data.unread_count").value(1))
                .andExpect(jsonPath("$.data.pagination.current_page").value(1));
    }

    @Test
    void unreadOnlyFilterExcludesReadNotifications() throws Exception {
        notificationFor(user, false);
        notificationFor(user, true);

        mockMvc.perform(get("/api/notifications").with(authentication(authenticatedAs(user))).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications", hasSize(1)));
    }

    @Test
    void doesNotListAnotherUsersNotifications() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger-notif")
                .email("stranger-notif@neobank.mx")
                .fullName("Stranger")
                .phone("+525500000000")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
        notificationFor(stranger, false);

        mockMvc.perform(get("/api/notifications").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications", hasSize(0)));
    }

    @Test
    void markAsRead_ownedNotification_persistsTheChange() throws Exception {
        Notification notification = notificationFor(user, false);

        mockMvc.perform(put("/api/notifications/{id}/read", notification.getId()).with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getRead()).isTrue();
    }

    @Test
    void markAsRead_unknownId_returns404NotA500() throws Exception {
        mockMvc.perform(put("/api/notifications/{id}/read", java.util.UUID.randomUUID()).with(authentication(authenticatedAs(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllAsRead_marksEveryOwnUnreadNotification() throws Exception {
        notificationFor(user, false);
        notificationFor(user, false);

        mockMvc.perform(put("/api/notifications/read-all").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(notificationRepository.countByUserIdAndReadFalse(user.getId())).isZero();
    }

    @Test
    void deleteNotification_ownedByCaller_removesIt() throws Exception {
        Notification notification = notificationFor(user, false);

        mockMvc.perform(delete("/api/notifications/{id}", notification.getId()).with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(notificationRepository.findById(notification.getId())).isEmpty();
    }

    @Test
    void registerDevice_persistsTheTokenForTheAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/notifications/register-device")
                        .with(authentication(authenticatedAs(user)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("device_token", "device-token-abc");
                            put("platform", "ios");
                        }})))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(deviceTokenRepository.findByToken("device-token-abc")).isPresent();
    }
}
