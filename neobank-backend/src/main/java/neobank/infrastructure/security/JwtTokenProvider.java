package neobank.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URL;
import java.util.Date;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final JWKSet jwkSet;
    private final String userPoolId;
    private final String region;

    public JwtTokenProvider(
            @Value("${aws.cognito.userPoolId}") String userPoolId,
            @Value("${aws.cognito.region}") String region) {
        this.userPoolId = userPoolId;
        this.region = region;
        this.jwkSet = loadJwkSet(userPoolId, region);
    }

    private JWKSet loadJwkSet(String userPoolId, String region) {
        try {
            String jwksUrl = String.format(
                    "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                    region, userPoolId
            );
            return JWKSet.load(new URL(jwksUrl));
        } catch (Exception e) {
            log.error("Failed to load Cognito JWKS", e);
            throw new RuntimeException("Failed to load Cognito JWKS", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String kid = signedJWT.getHeader().getKeyID();

            JWK jwk = jwkSet.getKeyByKeyId(kid);
            if (jwk == null) {
                log.error("No matching key found for kid: {}", kid);
                return false;
            }

            RSASSAVerifier verifier = new RSASSAVerifier((RSAKey) jwk);
            if (!signedJWT.verify(verifier)) {
                log.error("JWT signature verification failed");
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (new Date().after(claims.getExpirationTime())) {
                log.error("JWT token is expired");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            log.error("Error extracting userId from token", e);
            throw new RuntimeException("Invalid token", e);
        }
    }

    // Keep this for any internal token generation if needed
    public String generateToken(String userId) {
        throw new UnsupportedOperationException(
                "Token generation is handled by Cognito"
        );
    }
}