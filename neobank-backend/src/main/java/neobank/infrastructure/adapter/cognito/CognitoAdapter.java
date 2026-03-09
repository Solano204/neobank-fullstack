package neobank.infrastructure.adapter.cognito;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.config.CognitoConfig;
import neobank.infrastructure.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CognitoAdapter {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoConfig cognitoConfig;


    public void resendConfirmationCode(String email) {
        try {
            ResendConfirmationCodeRequest request = ResendConfirmationCodeRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(email)
                    .build();

            cognitoClient.resendConfirmationCode(request);

            log.info("Confirmation code resent to: {}", email);
        } catch (Exception e) {
            log.error("Error resending confirmation code", e);
            throw new RuntimeException("Error resending confirmation code", e);
        }
    }


    public String signUp(String email, String password, String fullName) {
        try {
            AttributeType emailAttr = AttributeType.builder()
                    .name("email")
                    .value(email)
                    .build();

            AttributeType nameAttr = AttributeType.builder()
                    .name("name")
                    .value(fullName)
                    .build();

            SignUpRequest request = SignUpRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(email)
                    .password(password)
                    .userAttributes(emailAttr, nameAttr)
                    .build();

            SignUpResponse response = cognitoClient.signUp(request);

            log.info("User signed up successfully: {}", response.userSub());

            return response.userSub();
        } catch (UsernameExistsException e) {
            throw new UnauthorizedException("EMAIL_ALREADY_EXISTS", "Email already exists");
        } catch (Exception e) {
            log.error("Error signing up user", e);
            throw new RuntimeException("Error signing up user", e);
        }
    }
    public void confirmSignUp(String email, String code) {
        try {
            ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(email)
                    .confirmationCode(code)
                    .build();

            cognitoClient.confirmSignUp(confirmRequest);

            log.info("Email confirmed successfully for: {}", email);
        } catch (CodeMismatchException e) {
            throw new UnauthorizedException("INVALID_CODE", "Invalid verification code");
        } catch (ExpiredCodeException e) {
            throw new UnauthorizedException("EXPIRED_CODE", "Verification code has expired");
        } catch (Exception e) {
            log.error("Error confirming sign up", e);
            throw new RuntimeException("Error confirming sign up", e);
        }
    }

    public Map<String, String> login(String email, String password) {
        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);
            if (password != null) {
                authParams.put("PASSWORD", password);
            }

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(cognitoConfig.getClientId())
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);

            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", authResponse.authenticationResult().accessToken());
            tokens.put("refreshToken", authResponse.authenticationResult().refreshToken());
            tokens.put("idToken", authResponse.authenticationResult().idToken());

            return tokens;
        } catch (NotAuthorizedException e) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        } catch (UserNotConfirmedException e) {
            throw new UnauthorizedException("USER_NOT_CONFIRMED", "Please verify your email first");
        } catch (Exception e) {
            log.error("Error logging in user", e);
            throw new RuntimeException("Error logging in user", e);
        }
    }

    public String refreshToken(String refreshToken) {
        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("REFRESH_TOKEN", refreshToken);

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(cognitoConfig.getClientId())
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);

            return authResponse.authenticationResult().accessToken();
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid refresh token");
        }
    }

    public void logout(String accessToken) {
        try {
            GlobalSignOutRequest request = GlobalSignOutRequest.builder()
                    .accessToken(accessToken)
                    .build();

            cognitoClient.globalSignOut(request);

            log.info("User logged out successfully");
        } catch (Exception e) {
            log.error("Error logging out user", e);
            throw new RuntimeException("Error logging out user", e);
        }
    }

    public void forgotPassword(String email) {
        try {
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(email)
                    .build();

            cognitoClient.forgotPassword(request);

            log.info("Password reset code sent to: {}", email);
        } catch (Exception e) {
            log.error("Error sending password reset code", e);
            throw new RuntimeException("Error sending password reset code", e);
        }
    }

    public void confirmForgotPassword(String email, String code, String newPassword) {
        try {
            ConfirmForgotPasswordRequest request = ConfirmForgotPasswordRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(email)
                    .confirmationCode(code)
                    .password(newPassword)
                    .build();

            cognitoClient.confirmForgotPassword(request);

            log.info("Password reset successfully for: {}", email);
        } catch (CodeMismatchException e) {
            throw new UnauthorizedException("INVALID_CODE", "Invalid reset code");
        } catch (Exception e) {
            log.error("Error resetting password", e);
            throw new RuntimeException("Error resetting password", e);
        }
    }

    public void changePassword(String accessToken, String oldPassword, String newPassword) {
        try {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .accessToken(accessToken)
                    .previousPassword(oldPassword)
                    .proposedPassword(newPassword)
                    .build();

            cognitoClient.changePassword(request);

            log.info("Password changed successfully");
        } catch (NotAuthorizedException e) {
            throw new UnauthorizedException("INVALID_PASSWORD", "Current password is incorrect");
        } catch (Exception e) {
            log.error("Error changing password", e);
            throw new RuntimeException("Error changing password", e);
        }
    }

    public void deleteUser(String email, String password) {
        try {
            Map<String, String> tokens = login(email, password);

            DeleteUserRequest request = DeleteUserRequest.builder()
                    .accessToken(tokens.get("accessToken"))
                    .build();

            cognitoClient.deleteUser(request);

            log.info("User deleted from Cognito: {}", email);
        } catch (Exception e) {
            log.error("Error deleting user from Cognito", e);
            throw new RuntimeException("Error deleting user", e);
        }
    }

    public void verifyPassword(String email, String password) {
        try {
            login(email, password);
        } catch (Exception e) {
            throw new UnauthorizedException("INVALID_PASSWORD", "Password is incorrect");
        }
    }

    public String getUserIdFromToken(String accessToken) {
        try {
            GetUserRequest request = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            GetUserResponse response = cognitoClient.getUser(request);

            return response.username();
        } catch (Exception e) {
            log.error("Error getting user from token", e);
            throw new UnauthorizedException("INVALID_TOKEN", "Invalid access token");
        }
    }
}