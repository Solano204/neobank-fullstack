package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.application.dto.response.SupportTicketResponse;
import neobank.application.usecase.support.ChatSupportUseCase;
import neobank.application.usecase.support.CreateSupportTicketUseCase;
import neobank.application.usecase.support.GetSupportTicketsUseCase;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    private final ChatSupportUseCase chatSupportUseCase;
    private final CreateSupportTicketUseCase createSupportTicketUseCase;
    private final GetSupportTicketsUseCase getSupportTicketsUseCase;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                 @RequestBody Map<String, String> request) {
        log.info("Chat request from user: {}", userPrincipal.getId());

        String message = request.get("message");
        String sessionId = request.getOrDefault("session_id", UUID.randomUUID().toString());

        ChatSupportUseCase.Result result = chatSupportUseCase.execute(userPrincipal.getId(), message);

        Map<String, Object> response = Map.of(
                "bot_response", result.message(),
                "session_id", sessionId,
                "intent", result.intent(),
                "confidence", result.confidence()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/faq")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFaq() {
        log.info("Get FAQ request");

        Map<String, Object> response = new java.util.HashMap<>();
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
    public ResponseEntity<ApiResponse<SupportTicketResponse>> createTicket(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                            @RequestBody Map<String, String> request) {
        log.info("Create ticket request from user: {}", userPrincipal.getId());

        SupportTicketResponse ticket = createSupportTicketUseCase.execute(
                userPrincipal.getId(),
                request.get("subject"),
                request.get("description"),
                request.get("priority")
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ticket));
    }

    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTickets(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get tickets request for user: {}", userPrincipal.getId());

        List<SupportTicketResponse> tickets = getSupportTicketsUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(Map.of("tickets", tickets)));
    }
}
