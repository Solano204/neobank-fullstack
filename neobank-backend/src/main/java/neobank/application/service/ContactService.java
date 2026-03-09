package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.AddContactRequest;
import neobank.application.dto.response.ContactResponse;
import neobank.application.usecase.contact.AddContactUseCase;
import neobank.application.usecase.contact.DeleteContactUseCase;
import neobank.application.usecase.contact.GetContactsUseCase;
import neobank.application.usecase.contact.UpdateFavoriteUseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final GetContactsUseCase getContactsUseCase;
    private final AddContactUseCase addContactUseCase;
    private final DeleteContactUseCase deleteContactUseCase;
    private final UpdateFavoriteUseCase updateFavoriteUseCase;

    public List<ContactResponse> getContacts(UUID userId) {
        return getContactsUseCase.execute(userId);
    }

    public ContactResponse addContact(UUID userId, AddContactRequest request) {
        return addContactUseCase.execute(userId, request);
    }

    public void deleteContact(UUID contactId, UUID userId) {
        deleteContactUseCase.execute(contactId, userId);
    }

    public void updateFavorite(UUID contactId, UUID userId, Boolean favorite) {
        updateFavoriteUseCase.execute(contactId, userId, favorite);
    }
}