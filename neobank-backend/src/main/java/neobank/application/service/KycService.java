package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.KycStatusResponse;
import neobank.application.dto.response.UploadUrlResponse;
import neobank.application.usecase.kyc.DeleteDocumentUseCase;
import neobank.application.usecase.kyc.GenerateUploadUrlUseCase;
import neobank.application.usecase.kyc.GetKycStatusUseCase;
import neobank.application.usecase.kyc.VerifyDocumentUseCase;
import neobank.domain.enums.DocumentType;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final GenerateUploadUrlUseCase generateUploadUrlUseCase;
    private final VerifyDocumentUseCase verifyDocumentUseCase;
    private final GetKycStatusUseCase getKycStatusUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    public UploadUrlResponse generateUploadUrl(UUID userId, String fileName, DocumentType documentType) {
        return generateUploadUrlUseCase.execute(userId, fileName, documentType);
    }

    public void verifyDocument(UUID userId, DocumentType documentType, String s3Key) {
        verifyDocumentUseCase.execute(userId, documentType, s3Key);
    }

    public KycStatusResponse getKycStatus(UUID userId) {
        return getKycStatusUseCase.execute(userId);
    }

    public void deleteDocument(UUID documentId, UUID userId) {
        deleteDocumentUseCase.execute(documentId, userId);
    }
}