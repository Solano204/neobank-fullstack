package neobank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "aws.s3")
@Data
public class S3Config {

    private String bucketName;

    // Doc 10: none of the 4 AWS SDK clients in this backend (S3, DynamoDB,
    // Cognito) had an explicit timeout - the SDK's own defaults are much
    // longer than acceptable for a user-facing request (on the order of
    // minutes), so a slow/stuck AWS call would hang whatever endpoint
    // depended on it far longer than a caller should ever wait.
    private static final ClientOverrideConfiguration AWS_CLIENT_TIMEOUTS = ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(10))
            .apiCallAttemptTimeout(Duration.ofSeconds(5))
            .build();

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(AWS_CLIENT_TIMEOUTS)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}