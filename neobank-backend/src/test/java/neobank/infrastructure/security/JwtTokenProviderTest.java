package neobank.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // NimbusJwtDecoder.withJwkSetUri(...).build() does not eagerly fetch
        // the JWKS -- the fetch only happens on first decode() -- so this is
        // safe to construct without a reachable Cognito endpoint.
        jwtTokenProvider = new JwtTokenProvider("us-east-1_fake", "us-east-1");
    }

    @Test
    void rejectsATokenThatIsNotValidJws() {
        assertThat(jwtTokenProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsAnEmptyToken() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void throwsWhenExtractingUserIdFromAnInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }
}
