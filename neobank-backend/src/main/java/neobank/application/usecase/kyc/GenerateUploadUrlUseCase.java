package neobank.application.usecase.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.UploadUrlResponse;
import neobank.domain.enums.DocumentType;
import neobank.infrastructure.adapter.s3.S3Adapter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateUploadUrlUseCase {

    private final S3Adapter s3Adapter;
    private static final long MAX_FILE_SIZE = 5242880L; // 5 MB
    private static final int URL_EXPIRATION_SECONDS = 300; // 5 minutes

    public UploadUrlResponse execute(UUID userId, String fileName, DocumentType documentType) {
        log.info("Generating upload URL for user: {} document: {}", userId, documentType);

        String s3Key = buildS3Key(userId, fileName);
        String uploadUrl = s3Adapter.generatePresignedUploadUrl(s3Key, URL_EXPIRATION_SECONDS);

        log.info("Upload URL generated successfully for user: {}", userId);

        return UploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .expiresIn(URL_EXPIRATION_SECONDS)
                .documentType(documentType.name())
                .maxFileSize(MAX_FILE_SIZE)
                .build();
    }

    private String buildS3Key(UUID userId, String fileName) {
        return String.format("kyc-docs/%s/%s", userId, fileName);
    }
}