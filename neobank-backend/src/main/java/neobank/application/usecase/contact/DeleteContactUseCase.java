package neobank.application.usecase.contact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.Contact;
import neobank.domain.repository.ContactRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteContactUseCase {

    private final ContactRepository contactRepository;

    @Transactional
    public void execute(UUID contactId, UUID userId) {
        log.info("Deleting contact: {} for user: {}", contactId, userId);

        Contact contact = contactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));

        contactRepository.delete(contact);

        log.info("Contact deleted successfully: {}", contactId);
    }
}