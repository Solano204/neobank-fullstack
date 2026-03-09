package neobank.infrastructure.health;


import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@RequiredArgsConstructor
public class CustomHealthIndicator implements HealthIndicator {

    private final CognitoIdentityProviderClient cognitoClient;
    private final S3Client s3Client;

    @Override
    public Health health() {
        try {
            // Check Cognito connectivity
            cognitoClient.listUserPools(builder -> builder.maxResults(1));

            // Check S3 connectivity
            s3Client.listBuckets();

            return Health.up()
                    .withDetail("cognito", "UP")
                    .withDetail("s3", "UP")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}