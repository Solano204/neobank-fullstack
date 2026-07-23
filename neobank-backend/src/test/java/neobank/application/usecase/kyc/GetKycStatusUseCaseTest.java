package neobank.application.usecase.kyc;

import neobank.application.dto.response.KycStatusResponse;
import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.KycDocumentRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetKycStatusUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @InjectMocks
    private GetKycStatusUseCase getKycStatusUseCase;

    @Test
    void reportsPendingForEveryDocumentTypeNotYetUploaded() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).kycStatus(KycStatus.PENDING).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByUserId(userId)).thenReturn(List.of());

        KycStatusResponse response = getKycStatusUseCase.execute(userId);

        assertThat(response.getDocuments()).hasSize(DocumentType.values().length);
        assertThat(response.getDocuments()).allMatch(doc -> doc.getStatus() == KycStatus.PENDING);
        assertThat(response.getVerificationProgress()).isZero();
    }

    @Test
    void calculatesProgressFromVerifiedRequiredDocuments() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).kycStatus(KycStatus.PROCESSING).build();
        KycDocument selfie = KycDocument.builder().documentType(DocumentType.SELFIE).status(KycStatus.VERIFIED).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByUserId(userId)).thenReturn(List.of(selfie));

        KycStatusResponse response = getKycStatusUseCase.execute(userId);

        assertThat(response.getVerificationProgress()).isEqualTo(50);
    }

    @Test
    void throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getKycStatusUseCase.execute(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
