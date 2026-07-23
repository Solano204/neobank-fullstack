package neobank.application.usecase.auth;

import neobank.application.dto.request.RefreshTokenRequest;
import neobank.application.dto.response.RefreshTokenResponse;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private RefreshTokenUseCase refreshTokenUseCase;

    @Test
    void returnsNewAccessTokenFromCognito() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-123").build();
        when(cognitoAdapter.refreshToken("refresh-123")).thenReturn("new-access-token");

        RefreshTokenResponse response = refreshTokenUseCase.execute(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getExpiresIn()).isEqualTo(3600);
    }

    @Test
    void propagatesInvalidRefreshToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("expired-token").build();
        when(cognitoAdapter.refreshToken("expired-token"))
                .thenThrow(new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid refresh token"));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(UnauthorizedException.class);
    }
}
