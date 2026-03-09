package neobank.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aws.cognito")
@Data
public class CognitoConfig {
    private String userPoolId;
    private String clientId;
    private String region;
}