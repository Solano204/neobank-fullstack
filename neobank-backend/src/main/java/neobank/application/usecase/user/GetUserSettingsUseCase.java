package neobank.application.usecase.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.UserSettings;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetUserSettingsUseCase {

    private final UserSettingsRepository userSettingsRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> execute(UUID userId) {
        log.info("Fetching settings for user: {}", userId);

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User settings not found"));

        Map<String, Object> response = new HashMap<>();

        Map<String, Boolean> notifications = new HashMap<>();
        notifications.put("email", settings.getEmailNotifications());
        notifications.put("push", settings.getPushNotifications());
        notifications.put("sms", settings.getSmsNotifications());
        response.put("notifications", notifications);

        Map<String, Boolean> security = new HashMap<>();
        security.put("mfaEnabled", settings.getMfaEnabled());
        security.put("biometricEnabled", settings.getBiometricEnabled());
        response.put("security", security);

        Map<String, String> preferences = new HashMap<>();
        preferences.put("language", settings.getLanguage());
        preferences.put("currency", settings.getCurrency());
        preferences.put("theme", settings.getTheme());
        response.put("preferences", preferences);

        return response;
    }
}