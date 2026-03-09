package neobank.application.usecase.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.KycStatusResponse;
import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.KycDocumentRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetKycStatusUseCase {

    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;

    @Transactional(readOnly = true)
    public KycStatusResponse execute(UUID userId) {
        log.info("Fetching KYC status for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<KycDocument> documents = kycDocumentRepository.findByUserId(userId);

        List<KycStatusResponse.DocumentDto> documentDtos = buildDocumentDtos(documents);

        int verificationProgress = calculateVerificationProgress(documents);

        return KycStatusResponse.builder()
                .kycStatus(user.getKycStatus())
                .documents(documentDtos)
                .verificationProgress(verificationProgress)
                .build();
    }

    private List<KycStatusResponse.DocumentDto> buildDocumentDtos(List<KycDocument> documents) {
        List<KycStatusResponse.DocumentDto> result = new ArrayList<>();

        for (DocumentType type : DocumentType.values()) {
            KycDocument doc = documents.stream()
                    .filter(d -> d.getDocumentType() == type)
                    .findFirst()
                    .orElse(null);

            if (doc != null) {
                result.add(KycStatusResponse.DocumentDto.builder()
                        .type(type)
                        .status(doc.getStatus())
                        .uploadedAt(doc.getCreatedAt())
                        .verifiedAt(doc.getVerifiedAt())
                        .aiConfidence(doc.getAiConfidence())
                        .rejectionReason(doc.getRejectionReason())
                        .build());
            } else {
                result.add(KycStatusResponse.DocumentDto.builder()
                        .type(type)
                        .status(KycStatus.PENDING)
                        .build());
            }
        }

        return result;
    }

    private int calculateVerificationProgress(List<KycDocument> documents) {
        long verifiedCount = documents.stream()
                .filter(d -> d.getStatus() == KycStatus.VERIFIED)
                .count();

        int totalRequired = 2; // SELFIE and ID_FRONT
        return (int) ((verifiedCount * 100) / totalRequired);
    }
}