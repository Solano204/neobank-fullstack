package neobank.application.usecase.auth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.RefreshTokenRequest;
import neobank.application.dto.response.RefreshTokenResponse;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenUseCase {

    private final CognitoAdapter cognitoAdapter;

    public RefreshTokenResponse execute(RefreshTokenRequest request) {
        log.info("Refreshing access token");

        String newAccessToken = cognitoAdapter.refreshToken(request.getRefreshToken());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(3600)
                .build();
    }
}