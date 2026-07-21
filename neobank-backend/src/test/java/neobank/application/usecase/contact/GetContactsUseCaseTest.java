package neobank.application.usecase.contact;

import neobank.application.dto.response.ContactResponse;
import neobank.application.usecase.mapper.ContactMapper;
import neobank.domain.entity.Contact;
import neobank.domain.repository.ContactRepository;
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
class GetContactsUseCaseTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ContactMapper contactMapper;

    @InjectMocks
    private GetContactsUseCase getContactsUseCase;

    @Test
    void returnsMappedContactsForUser() {
        UUID userId = UUID.randomUUID();
        Contact contact = Contact.builder().id(UUID.randomUUID()).build();
        ContactResponse response = ContactResponse.builder().id(contact.getId()).build();

        when(contactRepository.findByUserId(userId)).thenReturn(List.of(contact));
        when(contactMapper.toResponse(contact)).thenReturn(response);

        List<ContactResponse> result = getContactsUseCase.execute(userId);

        assertThat(result).containsExactly(response);
    }

    @Test
    void returnsEmptyListWhenNoContacts() {
        UUID userId = UUID.randomUUID();
        when(contactRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(getContactsUseCase.execute(userId)).isEmpty();
    }
}
