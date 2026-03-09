package neobank.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aws")
@Data
public class AwsConfig {
    private String accessKeyId;
    private String secretAccessKey;
    private String region;
}