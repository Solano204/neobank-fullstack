package neobank.application.usecase.support;

import neobank.application.dto.response.SupportTicketResponse;
import neobank.application.usecase.mapper.SupportTicketMapper;
import neobank.domain.entity.SupportTicket;
import neobank.domain.entity.User;
import neobank.domain.repository.SupportTicketRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSupportTicketUseCaseTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupportTicketMapper supportTicketMapper;

    @InjectMocks
    private CreateSupportTicketUseCase createSupportTicketUseCase;

    @Test
    void defaultsToLowPriorityWhenNoneGiven() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(supportTicketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(supportTicketMapper.toResponse(any())).thenReturn(SupportTicketResponse.builder().build());

        createSupportTicketUseCase.execute(userId, "Can't log in", "Password reset isn't working", null);

        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("LOW");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void rejectsAnUnrecognizedPriorityRatherThanTrustingClientInput() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(supportTicketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(supportTicketMapper.toResponse(any())).thenReturn(SupportTicketResponse.builder().build());

        createSupportTicketUseCase.execute(userId, "subject", "description", "URGENT!!!");

        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("LOW");
    }

    @Test
    void acceptsAValidPriorityCaseInsensitively() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(supportTicketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(supportTicketMapper.toResponse(any())).thenReturn(SupportTicketResponse.builder().build());

        createSupportTicketUseCase.execute(userId, "subject", "description", "high");

        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("HIGH");
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createSupportTicketUseCase.execute(userId, "subject", "description", null))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(supportTicketRepository);
    }
}
