package neobank.application.usecase.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.UpdateSettingsRequest;
import neobank.domain.entity.UserSettings;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateUserSettingsUseCase {

    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public void execute(UUID userId, UpdateSettingsRequest request) {
        log.info("Updating settings for user: {}", userId);

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User settings not found"));

        if (request.getNotifications() != null) {
            updateNotifications(settings, request.getNotifications());
        }

        if (request.getSecurity() != null) {
            updateSecurity(settings, request.getSecurity());
        }

        if (request.getPreferences() != null) {
            updatePreferences(settings, request.getPreferences());
        }

        userSettingsRepository.save(settings);

        log.info("Settings updated successfully for user: {}", userId);
    }

    private void updateNotifications(UserSettings settings, UpdateSettingsRequest.NotificationsDto notifications) {
        if (notifications.getEmail() != null) {
            settings.setEmailNotifications(notifications.getEmail());
        }
        if (notifications.getPush() != null) {
            settings.setPushNotifications(notifications.getPush());
        }
        if (notifications.getSms() != null) {
            settings.setSmsNotifications(notifications.getSms());
        }
    }

    private void updateSecurity(UserSettings settings, UpdateSettingsRequest.SecurityDto security) {
        if (security.getMfaEnabled() != null) {
            settings.setMfaEnabled(security.getMfaEnabled());
        }
        if (security.getBiometricEnabled() != null) {
            settings.setBiometricEnabled(security.getBiometricEnabled());
        }
    }

    private void updatePreferences(UserSettings settings, UpdateSettingsRequest.PreferencesDto preferences) {
        if (preferences.getLanguage() != null) {
            settings.setLanguage(preferences.getLanguage());
        }
        if (preferences.getCurrency() != null) {
            settings.setCurrency(preferences.getCurrency());
        }
        if (preferences.getTheme() != null) {
            settings.setTheme(preferences.getTheme());
        }
    }
}