package neobank.application.usecase.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.KycDocument;
import neobank.domain.repository.KycDocumentRepository;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteDocumentUseCase {

    private final KycDocumentRepository kycDocumentRepository;
    private final S3Adapter s3Adapter;

    @Transactional
    public void execute(UUID documentId, UUID userId) {
        log.info("Deleting document: {} for user: {}", documentId, userId);

        KycDocument document = kycDocumentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        s3Adapter.deleteObject(document.getS3Key());

        kycDocumentRepository.delete(document);

        log.info("Document deleted successfully: {}", documentId);
    }
}