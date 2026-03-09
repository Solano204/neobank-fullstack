package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.application.service.SecurityService;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {

    private final SecurityService securityService;

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessions(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get sessions request for user: {}", userPrincipal.getId());

        List<Map<String, Object>> sessions = securityService.getActiveSessions(userPrincipal.getId());

        Map<String, Object> response = Map.of("sessions", sessions);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<String>> terminateSession(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                @PathVariable UUID sessionId) {
        log.info("Terminate session request: {} for user: {}", sessionId, userPrincipal.getId());

        securityService.terminateSession(sessionId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("Session terminated"));
    }

    @DeleteMapping("/sessions/all")
    public ResponseEntity<ApiResponse<String>> terminateAllSessions(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Terminate all sessions request for user: {}", userPrincipal.getId());

        securityService.terminateAllSessions(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("All other sessions terminated"));
    }
}