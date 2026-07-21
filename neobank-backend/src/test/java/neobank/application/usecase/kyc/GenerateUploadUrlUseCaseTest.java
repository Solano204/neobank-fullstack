package neobank.application.usecase.kyc;

import neobank.application.dto.response.UploadUrlResponse;
import neobank.domain.enums.DocumentType;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateUploadUrlUseCaseTest {

    // kyc-docs/{userId}/{server-generated UUID}.jpg - the filename segment is
    // NOT the caller-supplied fileName (see the use case's own comment: a
    // deliberate security fix so an untrusted fileName never reaches S3 as
    // anything more than a validated extension), so it can't be asserted as
    // an exact string - only its shape.
    private static final Pattern S3_KEY_PATTERN = Pattern.compile(
            "^kyc-docs/[0-9a-f-]{36}/[0-9a-f-]{36}\\.jpg$");

    @Mock
    private S3Adapter s3Adapter;

    @InjectMocks
    private GenerateUploadUrlUseCase generateUploadUrlUseCase;

    @Test
    void buildsNamespacedKeyWithAServerGeneratedFilenameAndReturnsPresignedUrl() {
        UUID userId = UUID.randomUUID();
        when(s3Adapter.generatePresignedUploadUrl(anyString(), eq(300))).thenReturn("https://s3.example.com/presigned");

        UploadUrlResponse response = generateUploadUrlUseCase.execute(userId, "selfie.jpg", DocumentType.SELFIE);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(s3Adapter).generatePresignedUploadUrl(keyCaptor.capture(), eq(300));
        assertThat(keyCaptor.getValue()).matches(S3_KEY_PATTERN);
        assertThat(keyCaptor.getValue()).startsWith("kyc-docs/" + userId + "/");

        assertThat(response.getUploadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(response.getExpiresIn()).isEqualTo(300);
        assertThat(response.getDocumentType()).isEqualTo("SELFIE");
        assertThat(response.getMaxFileSize()).isEqualTo(5242880L);
    }

    @Test
    void twoUploadsForTheSameUserGetDifferentServerGeneratedKeys() {
        UUID userId = UUID.randomUUID();
        when(s3Adapter.generatePresignedUploadUrl(anyString(), anyInt())).thenReturn("https://s3.example.com/presigned");

        UploadUrlResponse first = generateUploadUrlUseCase.execute(userId, "a.jpg", DocumentType.SELFIE);
        UploadUrlResponse second = generateUploadUrlUseCase.execute(userId, "a.jpg", DocumentType.SELFIE);

        assertThat(first.getS3Key()).isNotEqualTo(second.getS3Key());
    }

    @Test
    void rejectsAFileNameWithNoExtension() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> generateUploadUrlUseCase.execute(userId, "selfie", DocumentType.SELFIE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("extension");
        verifyNoInteractions(s3Adapter);
    }

    @Test
    void rejectsANullFileName() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> generateUploadUrlUseCase.execute(userId, null, DocumentType.SELFIE))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(s3Adapter);
    }

    @Test
    void rejectsAnUnsupportedFileExtension() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> generateUploadUrlUseCase.execute(userId, "selfie.png", DocumentType.SELFIE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported file type");
        verifyNoInteractions(s3Adapter);
    }

    @Test
    void extensionMatchingIsCaseInsensitive() {
        UUID userId = UUID.randomUUID();
        when(s3Adapter.generatePresignedUploadUrl(anyString(), anyInt())).thenReturn("https://s3.example.com/presigned");

        UploadUrlResponse response = generateUploadUrlUseCase.execute(userId, "selfie.JPG", DocumentType.SELFIE);

        assertThat(response.getS3Key()).endsWith(".jpg");
    }
}
