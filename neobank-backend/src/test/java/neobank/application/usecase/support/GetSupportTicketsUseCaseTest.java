package neobank.application.usecase.support;

import neobank.application.dto.response.SupportTicketResponse;
import neobank.application.usecase.mapper.SupportTicketMapper;
import neobank.domain.entity.SupportTicket;
import neobank.domain.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSupportTicketsUseCaseTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportTicketMapper supportTicketMapper;

    @InjectMocks
    private GetSupportTicketsUseCase getSupportTicketsUseCase;

    private final UUID userId = UUID.randomUUID();

    @Test
    void returnsTheUsersTicketsMappedToResponses() {
        SupportTicket ticket = SupportTicket.builder().id(UUID.randomUUID()).subject("Card not working").build();
        when(supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(ticket));
        when(supportTicketMapper.toResponse(ticket)).thenReturn(SupportTicketResponse.builder().id(ticket.getId()).subject("Card not working").build());

        List<SupportTicketResponse> tickets = getSupportTicketsUseCase.execute(userId);

        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getSubject()).isEqualTo("Card not working");
    }

    @Test
    void noTickets_returnsEmptyListNotNull() {
        when(supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(getSupportTicketsUseCase.execute(userId)).isEmpty();
    }
}
