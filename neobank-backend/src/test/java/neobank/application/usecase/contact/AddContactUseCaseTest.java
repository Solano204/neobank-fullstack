package neobank.application.usecase.contact;

import neobank.application.dto.request.AddContactRequest;
import neobank.application.dto.response.ContactResponse;
import neobank.application.usecase.mapper.ContactMapper;
import neobank.domain.entity.Account;
import neobank.domain.entity.Contact;
import neobank.domain.entity.User;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.ContactRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddContactUseCaseTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ContactMapper contactMapper;

    @InjectMocks
    private AddContactUseCase addContactUseCase;

    private final UUID userId = UUID.randomUUID();
    private final AddContactRequest request = AddContactRequest.builder()
            .accountNumber("123456789012345678")
            .nickname("Mom")
            .build();

    @Test
    void addsNewContact() {
        User owner = User.builder().id(userId).build();
        User recipientOwner = User.builder().fullName("Grace Hopper").build();
        Account recipientAccount = Account.builder().user(recipientOwner).build();
        Contact saved = Contact.builder().id(UUID.randomUUID()).build();
        ContactResponse expected = ContactResponse.builder().id(saved.getId()).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(accountRepository.findByAccountNumber("123456789012345678")).thenReturn(Optional.of(recipientAccount));
        when(contactRepository.existsByUserAndAccountNumber(owner, "123456789012345678")).thenReturn(false);
        when(contactRepository.save(any(Contact.class))).thenReturn(saved);
        when(contactMapper.toResponse(saved)).thenReturn(expected);

        ContactResponse response = addContactUseCase.execute(userId, request);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void throwsWhenOwnerNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addContactUseCase.execute(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenRecipientAccountNotFound() {
        User owner = User.builder().id(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(accountRepository.findByAccountNumber("123456789012345678")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addContactUseCase.execute(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenContactAlreadyExists() {
        User owner = User.builder().id(userId).build();
        Account recipientAccount = Account.builder().user(User.builder().fullName("Grace Hopper").build()).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(accountRepository.findByAccountNumber("123456789012345678")).thenReturn(Optional.of(recipientAccount));
        when(contactRepository.existsByUserAndAccountNumber(owner, "123456789012345678")).thenReturn(true);

        assertThatThrownBy(() -> addContactUseCase.execute(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Contact already exists");
    }
}
