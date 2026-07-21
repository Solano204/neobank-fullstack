package neobank.application.usecase.notification;

import neobank.domain.entity.DeviceToken;
import neobank.domain.entity.User;
import neobank.domain.repository.DeviceTokenRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class RegisterDeviceTokenUseCaseTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RegisterDeviceTokenUseCase registerDeviceTokenUseCase;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.builder().id(userId).build();

    @Test
    void newToken_createdAndAssociatedWithTheUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("new-token")).thenReturn(Optional.empty());

        registerDeviceTokenUseCase.execute(userId, "new-token", "ios");

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("new-token");
        assertThat(captor.getValue().getPlatform()).isEqualTo("ios");
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    // The same physical device's push token is re-registered every app
    // launch - it must be re-pointed at the (possibly different) current
    // user rather than duplicated as a second row for the same token.
    @Test
    void existingToken_reassignedToTheNewUserInsteadOfDuplicated() {
        User previousOwner = User.builder().id(UUID.randomUUID()).build();
        DeviceToken existing = DeviceToken.builder().id(UUID.randomUUID()).token("shared-device-token").user(previousOwner).platform("android").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("shared-device-token")).thenReturn(Optional.of(existing));

        registerDeviceTokenUseCase.execute(userId, "shared-device-token", "ios");

        verify(deviceTokenRepository).save(existing);
        assertThat(existing.getUser()).isEqualTo(user);
        assertThat(existing.getPlatform()).isEqualTo("ios");
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerDeviceTokenUseCase.execute(userId, "token", "ios"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(deviceTokenRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
