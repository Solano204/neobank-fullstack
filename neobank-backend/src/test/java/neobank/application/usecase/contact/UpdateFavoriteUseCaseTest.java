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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateFavoriteUseCaseTest {

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private UpdateFavoriteUseCase updateFavoriteUseCase;

    @Test
    void marksContactAsFavorite() {
        UUID contactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Contact contact = Contact.builder().id(contactId).favorite(false).build();
        when(contactRepository.findByIdAndUserId(contactId, userId)).thenReturn(Optional.of(contact));

        updateFavoriteUseCase.execute(contactId, userId, true);

        assertThat(contact.getFavorite()).isTrue();
        verify(contactRepository).save(contact);
    }

    @Test
    void throwsWhenContactNotFound() {
        UUID contactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(contactRepository.findByIdAndUserId(contactId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateFavoriteUseCase.execute(contactId, userId, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
