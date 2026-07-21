package neobank.application.usecase.kyc;

import neobank.domain.entity.KycDocument;
import neobank.domain.repository.KycDocumentRepository;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteDocumentUseCaseTest {

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Mock
    private S3Adapter s3Adapter;

    @InjectMocks
    private DeleteDocumentUseCase deleteDocumentUseCase;

    @Test
    void deletesDocumentFromS3AndDatabase() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        KycDocument document = KycDocument.builder().id(documentId).s3Key("kyc-docs/" + userId + "/id.jpg").build();
        when(kycDocumentRepository.findByIdAndUserId(documentId, userId)).thenReturn(Optional.of(document));

        deleteDocumentUseCase.execute(documentId, userId);

        verify(s3Adapter).deleteObject("kyc-docs/" + userId + "/id.jpg");
        verify(kycDocumentRepository).delete(document);
    }

    @Test
    void throwsWhenDocumentNotFoundForUser() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(kycDocumentRepository.findByIdAndUserId(documentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteDocumentUseCase.execute(documentId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(s3Adapter);
    }
}
