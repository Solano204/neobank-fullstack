package neobank.application.usecase.user;

import neobank.application.dto.request.UpdateSettingsRequest;
import neobank.domain.entity.UserSettings;
import neobank.domain.repository.UserSettingsRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserSettingsUseCaseTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UpdateUserSettingsUseCase updateUserSettingsUseCase;

    @Test
    void updatesOnlyProvidedSections() {
        UUID userId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder()
                .emailNotifications(true)
                .pushNotifications(true)
                .smsNotifications(false)
                .mfaEnabled(false)
                .biometricEnabled(false)
                .theme("light")
                .build();
        UpdateSettingsRequest request = UpdateSettingsRequest.builder()
                .security(UpdateSettingsRequest.SecurityDto.builder().mfaEnabled(true).build())
                .build();

        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));

        updateUserSettingsUseCase.execute(userId, request);

        assertThat(settings.getMfaEnabled()).isTrue();
        assertThat(settings.getEmailNotifications()).isTrue();
        assertThat(settings.getTheme()).isEqualTo("light");
        verify(userSettingsRepository).save(settings);
    }

    @Test
    void throwsWhenSettingsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUserSettingsUseCase.execute(userId, UpdateSettingsRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
