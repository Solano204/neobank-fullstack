package neobank.application.usecase.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.SupportTicketResponse;
import neobank.application.usecase.mapper.SupportTicketMapper;
import neobank.domain.repository.SupportTicketRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetSupportTicketsUseCase {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMapper supportTicketMapper;

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> execute(UUID userId) {
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(supportTicketMapper::toResponse)
                .toList();
    }
}
