package neobank.application.usecase.user;

import neobank.domain.entity.UserSettings;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserSettingsUseCaseTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private GetUserSettingsUseCase getUserSettingsUseCase;

    @Test
    void groupsSettingsIntoNotificationsSecurityAndPreferences() {
        UUID userId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder()
                .emailNotifications(true)
                .pushNotifications(false)
                .smsNotifications(false)
                .mfaEnabled(true)
                .biometricEnabled(false)
                .language("es-MX")
                .currency("MXN")
                .theme("dark")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));

        Map<String, Object> response = getUserSettingsUseCase.execute(userId);

        assertThat((Map<String, Boolean>) response.get("notifications"))
                .containsEntry("email", true)
                .containsEntry("push", false)
                .containsEntry("sms", false);
        assertThat((Map<String, Boolean>) response.get("security"))
                .containsEntry("mfaEnabled", true)
                .containsEntry("biometricEnabled", false);
        assertThat((Map<String, String>) response.get("preferences"))
                .containsEntry("language", "es-MX")
                .containsEntry("currency", "MXN")
                .containsEntry("theme", "dark");
    }

    @Test
    void throwsWhenSettingsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUserSettingsUseCase.execute(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
