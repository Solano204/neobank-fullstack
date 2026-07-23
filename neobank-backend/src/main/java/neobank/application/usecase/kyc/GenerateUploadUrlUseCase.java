package neobank.application.usecase.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.UploadUrlResponse;
import neobank.domain.enums.DocumentType;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateUploadUrlUseCase {

    private final S3Adapter s3Adapter;
    private static final long MAX_FILE_SIZE = 5242880L; // 5 MB
    private static final int URL_EXPIRATION_SECONDS = 300; // 5 minutes
    // S3Adapter presigns a fixed Content-Type: image/jpeg, so this is the
    // only extension that actually matches what the presigned PUT will
    // accept - listed explicitly rather than trusting the caller-supplied
    // fileName's extension blindly.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg");

    public UploadUrlResponse execute(UUID userId, String fileName, DocumentType documentType) {
        log.info("Generating upload URL for user: {} document: {}", userId, documentType);

        String s3Key = buildS3Key(userId, fileName);
        String uploadUrl = s3Adapter.generatePresignedUploadUrl(s3Key, URL_EXPIRATION_SECONDS);

        log.info("Upload URL generated successfully for user: {}", userId);

        return UploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .s3Key(s3Key)
                .expiresIn(URL_EXPIRATION_SECONDS)
                .documentType(documentType.name())
                .maxFileSize(MAX_FILE_SIZE)
                .build();
    }

    // Was String.format("kyc-docs/%s/%s", userId, fileName) - the raw,
    // unsanitized client-supplied fileName went straight into the S3 key.
    // userId is a server-generated UUID (safe), but fileName was whatever
    // the caller sent: no length limit, no character restrictions, no
    // extension check. Building the key from a server-generated UUID + a
    // validated extension instead means the untrusted part of the request
    // never reaches S3 as anything more than a validated 3-4 character
    // suffix.
    private String buildS3Key(UUID userId, String fileName) {
        String extension = extractValidatedExtension(fileName);
        return String.format("kyc-docs/%s/%s.%s", userId, UUID.randomUUID(), extension);
    }

    private String extractValidatedExtension(String fileName) {
        if (fileName == null) {
            throw new BusinessException("INVALID_FILE_NAME", "File name is required");
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("INVALID_FILE_NAME", "File name must have an extension");
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE",
                    "Unsupported file type: ." + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }
        return extension;
    }
}