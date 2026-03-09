package neobank;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.sql.*;
import java.util.List;

public class KycValidatorHandler implements RequestHandler<S3Event, String> {

    private static final S3Client s3Client = S3Client.create();
    private static final RekognitionClient rekognitionClient = RekognitionClient.create();
    private static final SnsClient snsClient = SnsClient.create();
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String SNS_TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        try {
            String bucketName = event.getRecords().get(0).getS3().getBucket().getName();
            String objectKey = event.getRecords().get(0).getS3().getObject().getKey();

            context.getLogger().log("Processing KYC document: " + bucketName + "/" + objectKey);

            byte[] imageBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()
            ).asByteArray();

            context.getLogger().log("Image downloaded, size: " + imageBytes.length + " bytes");

            DetectFacesResponse aiResponse = rekognitionClient.detectFaces(DetectFacesRequest.builder()
                    .image(Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build())
                    .attributes(Attribute.ALL)
                    .build()
            );

            String status = "REJECTED";
            String rejectionReason = null;
            Double confidence = 0.0;

            List<FaceDetail> faceDetails = aiResponse.faceDetails();
            if (!faceDetails.isEmpty()) {
                FaceDetail face = faceDetails.get(0);
                confidence = face.confidence().doubleValue();

                boolean eyesOpen = face.eyesOpen().value();
                double sharpness = face.quality().sharpness();
                double brightness = face.quality().brightness();

                context.getLogger().log(String.format(
                        "AI Analysis - Confidence: %.2f%%, Eyes Open: %b, Sharpness: %.2f, Brightness: %.2f",
                        confidence, eyesOpen, sharpness, brightness
                ));

                if (confidence > 90.0 && eyesOpen && sharpness > 80.0 && brightness > 70.0) {
                    status = "VERIFIED";
                    context.getLogger().log("Document VERIFIED");
                } else {
                    rejectionReason = buildRejectionReason(confidence, eyesOpen, sharpness, brightness);
                    context.getLogger().log("Document REJECTED: " + rejectionReason);
                }
            } else {
                rejectionReason = "No face detected in the image";
                context.getLogger().log("No face detected");
            }

            String userId = extractUserIdFromKey(objectKey);
            updateKycStatus(objectKey, userId, status, confidence, rejectionReason, context);

            if ("VERIFIED".equals(status)) {
                sendNotification(userId, "KYC Verified",
                        "Your identity has been verified successfully. You can now use all features.");
            } else {
                sendNotification(userId, "KYC Rejected",
                        "Document verification failed. Reason: " + rejectionReason);
            }

            return "SUCCESS";

        } catch (Exception e) {
            context.getLogger().log("Error processing KYC: " + e.getMessage());
            e.printStackTrace();
            return "FAILED";
        }
    }

    private String buildRejectionReason(double confidence, boolean eyesOpen,
                                        double sharpness, double brightness) {
        if (confidence <= 90.0) {
            return "Face detection confidence too low. Please ensure clear visibility of face.";
        }
        if (!eyesOpen) {
            return "Eyes must be open in the photo.";
        }
        if (sharpness <= 80.0) {
            return "Image is too blurry. Please retake with better focus.";
        }
        if (brightness <= 70.0) {
            return "Image is too dark. Please retake in better lighting.";
        }
        return "Image quality does not meet requirements.";
    }

    private String extractUserIdFromKey(String s3Key) {
        String[] parts = s3Key.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        throw new IllegalArgumentException("Invalid S3 key format: " + s3Key);
    }

    private void updateKycStatus(String s3Key, String userId, String status,
                                 Double confidence, String rejectionReason, Context context) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String updateDocSql =
                    "UPDATE kyc_documents SET " +
                            "status = ?, " +
                            "ai_confidence = ?, " +
                            "rejection_reason = ?, " +
                            "verified_at = CASE WHEN ? = 'VERIFIED' THEN NOW() ELSE NULL END, " +
                            "updated_at = NOW() " +
                            "WHERE s3_key = ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateDocSql)) {
                stmt.setString(1, status);
                stmt.setDouble(2, confidence);
                stmt.setString(3, rejectionReason);
                stmt.setString(4, status);
                stmt.setString(5, s3Key);
                int updated = stmt.executeUpdate();

                context.getLogger().log("Updated " + updated + " kyc_documents records");
            }

            if ("VERIFIED".equals(status)) {
                String updateUserSql =
                        "UPDATE users SET " +
                                "kyc_status = 'VERIFIED', " +
                                "kyc_verified_at = NOW(), " +
                                "updated_at = NOW() " +
                                "WHERE id = ?::uuid";

                try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                    stmt.setString(1, userId);
                    int updated = stmt.executeUpdate();

                    context.getLogger().log("Updated " + updated + " users records");
                }
            }
        }
    }

    private void sendNotification(String userId, String subject, String message) {
        try {
            String fullMessage = String.format(
                    "User ID: %s\nSubject: %s\n\n%s",
                    userId, subject, message
            );

            snsClient.publish(PublishRequest.builder()
                    .topicArn(SNS_TOPIC_ARN)
                    .subject(subject)
                    .message(fullMessage)
                    .build()
            );
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }
}