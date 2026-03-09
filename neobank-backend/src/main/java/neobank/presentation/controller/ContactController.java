package neobank.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.AddContactRequest;
import neobank.application.dto.response.ApiResponse;
import neobank.application.dto.response.ContactResponse;
import neobank.application.usecase.contact.AddContactUseCase;
import neobank.application.usecase.contact.DeleteContactUseCase;
import neobank.application.usecase.contact.GetContactsUseCase;
import neobank.application.usecase.contact.UpdateFavoriteUseCase;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final GetContactsUseCase getContactsUseCase;
    private final AddContactUseCase addContactUseCase;
    private final DeleteContactUseCase deleteContactUseCase;
    private final UpdateFavoriteUseCase updateFavoriteUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContacts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get contacts request for user: {}", userPrincipal.getId());

        List<ContactResponse> contacts = getContactsUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContactResponse>> addContact(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                   @Valid @RequestBody AddContactRequest request) {
        log.info("Add contact request for user: {}", userPrincipal.getId());

        ContactResponse contact = addContactUseCase.execute(userPrincipal.getId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contact added successfully", contact));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<ApiResponse<String>> deleteContact(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                             @PathVariable UUID contactId) {
        log.info("Delete contact request: {} for user: {}", contactId, userPrincipal.getId());

        deleteContactUseCase.execute(contactId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("Contact deleted"));
    }

    @PutMapping("/{contactId}/favorite")
    public ResponseEntity<ApiResponse<String>> updateFavorite(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @PathVariable UUID contactId,
                                                              @RequestParam Boolean favorite) {
        log.info("Update favorite request for contact: {} user: {}", contactId, userPrincipal.getId());

        updateFavoriteUseCase.execute(contactId, userPrincipal.getId(), favorite);

        return ResponseEntity.ok(ApiResponse.success("Favorite status updated"));
    }
}