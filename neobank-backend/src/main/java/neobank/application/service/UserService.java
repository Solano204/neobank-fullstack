package neobank.application.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.UpdateProfileRequest;
import neobank.application.dto.request.UpdateSettingsRequest;
import neobank.application.dto.response.UserProfileResponse;
import neobank.application.usecase.user.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final GetUserSettingsUseCase getUserSettingsUseCase;
    private final UpdateUserSettingsUseCase updateUserSettingsUseCase;
    private final DeleteUserAccountUseCase deleteUserAccountUseCase;

    public UserProfileResponse getProfile(UUID userId) {
        return getUserProfileUseCase.execute(userId);
    }

    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        return updateUserProfileUseCase.execute(userId, request);
    }

    public Map<String, Object> getSettings(UUID userId) {
        return getUserSettingsUseCase.execute(userId);
    }

    public void updateSettings(UUID userId, UpdateSettingsRequest request) {
        updateUserSettingsUseCase.execute(userId, request);
    }

    public void deleteAccount(UUID userId, String password) {
        deleteUserAccountUseCase.execute(userId, password);
    }
}