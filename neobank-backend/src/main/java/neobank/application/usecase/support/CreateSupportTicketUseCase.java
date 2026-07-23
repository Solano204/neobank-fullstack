package neobank.application.usecase.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.SupportTicketResponse;
import neobank.application.usecase.mapper.SupportTicketMapper;
import neobank.domain.entity.SupportTicket;
import neobank.domain.entity.User;
import neobank.domain.repository.SupportTicketRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateSupportTicketUseCase {

    private static final java.util.Set<String> VALID_PRIORITIES = java.util.Set.of("LOW", "MEDIUM", "HIGH");

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final SupportTicketMapper supportTicketMapper;

    @Transactional
    public SupportTicketResponse execute(UUID userId, String subject, String description, String priority) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String normalizedPriority = priority != null && VALID_PRIORITIES.contains(priority.toUpperCase())
                ? priority.toUpperCase() : "LOW";

        SupportTicket ticket = SupportTicket.builder()
                .user(user)
                .subject(subject)
                .description(description)
                .priority(normalizedPriority)
                .status("OPEN")
                .build();

        ticket = supportTicketRepository.save(ticket);
        log.info("Support ticket {} created for user {}", ticket.getId(), userId);

        return supportTicketMapper.toResponse(ticket);
    }
}
