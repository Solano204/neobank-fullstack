package neobank.application.usecase.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.KycDocumentRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerifyDocumentUseCase {

    private final KycDocumentRepository kycDocumentRepository;
    private final UserRepository userRepository;

    @Transactional
    public void execute(UUID userId, DocumentType documentType, String s3Key) {
        log.info("Verifying document for user: {} type: {}", userId, documentType);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        KycDocument document = KycDocument.builder()
                .user(user)
                .documentType(documentType)
                .s3Key(s3Key)
                .status(KycStatus.PROCESSING)
                .build();

        kycDocumentRepository.save(document);

        log.info("Document submitted for verification: {}", document.getId());
    }
}