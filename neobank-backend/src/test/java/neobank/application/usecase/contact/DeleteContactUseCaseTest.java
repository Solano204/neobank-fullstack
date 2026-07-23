package neobank.application.usecase.contact;

import neobank.domain.entity.Contact;
import neobank.domain.repository.ContactRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteContactUseCaseTest {

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private DeleteContactUseCase deleteContactUseCase;

    @Test
    void deletesContactOwnedByUser() {
        UUID contactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Contact contact = Contact.builder().id(contactId).build();
        when(contactRepository.findByIdAndUserId(contactId, userId)).thenReturn(Optional.of(contact));

        deleteContactUseCase.execute(contactId, userId);

        verify(contactRepository).delete(contact);
    }

    @Test
    void throwsWhenContactNotFoundForUser() {
        UUID contactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(contactRepository.findByIdAndUserId(contactId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteContactUseCase.execute(contactId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
