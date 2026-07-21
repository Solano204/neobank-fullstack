package neobank.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Validates Cognito-issued access tokens. Backed by NimbusJwtDecoder, which
 * lazily fetches the JWKS on first use and caches/auto-refreshes it —
 * unlike a one-shot fetch in the constructor, a Cognito key rotation or a
 * momentary network blip here doesn't take the whole app down or leave it
 * validating against a stale key set until restart.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final JwtDecoder jwtDecoder;

    public JwtTokenProvider(
            @Value("${aws.cognito.userPoolId}") String userPoolId,
            @Value("${aws.cognito.region}") String region) {
        String jwksUrl = String.format(
                "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                region, userPoolId
        );
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
    }

    public boolean validateToken(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return jwt.getSubject();
        } catch (Exception e) {
            log.error("Error extracting userId from token", e);
            throw new RuntimeException("Invalid token", e);
        }
    }
}
