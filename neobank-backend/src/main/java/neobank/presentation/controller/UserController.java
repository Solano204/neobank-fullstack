package neobank.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.UpdateProfileRequest;
import neobank.application.dto.request.UpdateSettingsRequest;
import neobank.application.dto.response.ApiResponse;
import neobank.application.dto.response.UserProfileResponse;
import neobank.application.usecase.user.*;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final GetUserSettingsUseCase getUserSettingsUseCase;
    private final UpdateUserSettingsUseCase updateUserSettingsUseCase;
    private final DeleteUserAccountUseCase deleteUserAccountUseCase;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get profile request for user: {}", userPrincipal.getId());

        UserProfileResponse response = getUserProfileUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                          @Valid @RequestBody UpdateProfileRequest request) {
        log.info("Update profile request for user: {}", userPrincipal.getId());

        UserProfileResponse response = updateUserProfileUseCase.execute(userPrincipal.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get settings request for user: {}", userPrincipal.getId());

        Map<String, Object> response = getUserSettingsUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<String>> updateSettings(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @Valid @RequestBody UpdateSettingsRequest request) {
        log.info("Update settings request for user: {}", userPrincipal.getId());

        updateUserSettingsUseCase.execute(userPrincipal.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully"));
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                             @RequestParam String password) {
        log.info("Delete account request for user: {}", userPrincipal.getId());

        deleteUserAccountUseCase.execute(userPrincipal.getId(), password);

        return ResponseEntity.ok(ApiResponse.success("Account deletion scheduled. You have 30 days to cancel."));
    }
}