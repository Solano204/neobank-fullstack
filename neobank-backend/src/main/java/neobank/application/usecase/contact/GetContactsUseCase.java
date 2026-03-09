package neobank.application.usecase.contact;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ContactResponse;
import neobank.application.usecase.mapper.ContactMapper;
import neobank.domain.entity.Contact;
import neobank.domain.repository.ContactRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetContactsUseCase {

    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;

    @Transactional(readOnly = true)
    public List<ContactResponse> execute(UUID userId) {
        log.info("Fetching contacts for user: {}", userId);

        List<Contact> contacts = contactRepository.findByUserId(userId);

        return contacts.stream()
                .map(contactMapper::toResponse)
                .toList();
    }
}