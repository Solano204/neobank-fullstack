package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.UserSession;
import neobank.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final UserSessionRepository userSessionRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessions(UUID userId) {
        List<UserSession> sessions = userSessionRepository.findByUserId(userId);

        return sessions.stream()
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(s -> {
                    Map<String, Object> session = new HashMap<>();
                    session.put("id", s.getId());
                    session.put("device", s.getDeviceName());
                    session.put("location", extractLocation(s.getIpAddress()));
                    session.put("ip_address", s.getIpAddress());
                    session.put("last_active", s.getLastActiveAt());
                    return session;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void terminateSession(UUID sessionId, UUID userId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        userSessionRepository.delete(session);
        log.info("Session terminated: {}", sessionId);
    }

    @Transactional
    public void terminateAllSessions(UUID userId) {
        userSessionRepository.deleteByUserId(userId);
        log.info("All sessions terminated for user: {}", userId);
    }

    private String extractLocation(String ipAddress) {
        return "Unknown";
    }
}