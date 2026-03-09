package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                 @RequestBody Map<String, String> request) {
        log.info("Chat request from user: {}", userPrincipal.getId());

        String message = request.get("message");
        String sessionId = request.getOrDefault("session_id", UUID.randomUUID().toString());

        Map<String, Object> response = new HashMap<>();
        response.put("bot_response", "I'm here to help! For specific transaction issues, please contact support.");
        response.put("session_id", sessionId);
        response.put("intent", "GeneralQuery");
        response.put("confidence", 0.75);
        response.put("suggested_actions", List.of(
                Map.of("label", "View Balance", "action", "navigate", "target", "/accounts/balance"),
                Map.of("label", "Contact Support", "action", "navigate", "target", "/support/ticket")
        ));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/faq")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFaq() {
        log.info("Get FAQ request");

        Map<String, Object> response = new HashMap<>();
        response.put("categories", List.of(
                Map.of(
                        "id", "transfers",
                        "name", "Transfers",
                        "questions", List.of(
                                Map.of("id", "faq_1", "question", "How long do transfers take?",
                                        "answer", "Transfers are usually instant within NeoBank. External transfers take 1-3 business days."),
                                Map.of("id", "faq_2", "question", "What's the transfer limit?",
                                        "answer", "You can transfer up to $50,000 MXN per day.")
                        )
                )
        ));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/ticket")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTicket(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                         @RequestBody Map<String, String> request) {
        log.info("Create ticket request from user: {}", userPrincipal.getId());

        String ticketId = "TICKET-" + System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("ticket_id", ticketId);
        response.put("status", "OPEN");
        response.put("created_at", LocalDateTime.now());
        response.put("estimated_response_time", "24 hours");

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTickets(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get tickets request for user: {}", userPrincipal.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("tickets", new ArrayList<>());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}