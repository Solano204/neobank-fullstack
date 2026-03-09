package neobank.application.usecase.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddContactUseCase {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ContactMapper contactMapper;

    @Transactional
    public ContactResponse execute(UUID userId, AddContactRequest request) {
        log.info("Adding contact for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account recipientAccount = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (contactRepository.existsByUserAndAccountNumber(user, request.getAccountNumber())) {
            throw new BusinessException("CONTACT_EXISTS", "Contact already exists");
        }

        Contact contact = Contact.builder()
                .user(user)
                .accountNumber(request.getAccountNumber())
                .contactName(recipientAccount.getUser().getFullName())
                .nickname(request.getNickname())
                .favorite(false)
                .build();

        Contact savedContact = contactRepository.save(contact);

        log.info("Contact added successfully: {}", savedContact.getId());

        return contactMapper.toResponse(savedContact);
    }
}