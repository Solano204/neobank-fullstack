package neobank.infrastructure.adapter.s3;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.config.S3Config;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3Adapter {

    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    public String generatePresignedUploadUrl(String key, int expirationSeconds) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .contentType("image/jpeg")
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .putObjectRequest(putObjectRequest)
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            String url = presignedRequest.url().toString();

            log.info("Generated presigned upload URL for key: {}", key);

            return url;
        } catch (Exception e) {
            log.error("Error generating presigned URL", e);
            throw new RuntimeException("Error generating presigned URL", e);
        }
    }

    public void deleteObject(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();

            s3Config.s3Client().deleteObject(deleteRequest);

            log.info("Deleted object from S3: {}", key);
        } catch (Exception e) {
            log.error("Error deleting object from S3", e);
            throw new RuntimeException("Error deleting object from S3", e);
        }
    }
}