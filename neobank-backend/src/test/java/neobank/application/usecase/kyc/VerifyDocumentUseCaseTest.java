package neobank.application.usecase.kyc;

import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.KycDocumentRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyDocumentUseCaseTest {

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VerifyDocumentUseCase verifyDocumentUseCase;

    @Test
    void savesDocumentAsProcessing() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        verifyDocumentUseCase.execute(userId, DocumentType.ID_FRONT, "kyc-docs/" + userId + "/id-front.jpg");

        ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
        verify(kycDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(KycStatus.PROCESSING);
        assertThat(captor.getValue().getDocumentType()).isEqualTo(DocumentType.ID_FRONT);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyDocumentUseCase.execute(userId, DocumentType.SELFIE, "key"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
