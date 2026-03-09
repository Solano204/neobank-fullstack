package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.User;
import neobank.domain.entity.UserSession;
import neobank.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository userSessionRepository;

    @Transactional
    public UserSession createSession(User user, String token, String deviceName, String ipAddress, String userAgent) {
        UserSession session = UserSession.builder()
                .user(user)
                .sessionToken(token)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .lastActiveAt(LocalDateTime.now())
                .build();

        return userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<UserSession> getUserSessions(UUID userId) {
        return userSessionRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        userSessionRepository.delete(session);
    }

    @Transactional
    public void deleteAllUserSessions(UUID userId) {
        userSessionRepository.deleteByUserId(userId);
    }

    @Transactional
    public void cleanExpiredSessions() {
        log.info("Cleaning up expired sessions");
        userSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}