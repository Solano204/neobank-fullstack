package neobank.application.usecase.user;

import neobank.domain.entity.User;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.ResourceNotFoundException;
import neobank.infrastructure.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteUserAccountUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CognitoAdapter cognitoAdapter;

    @InjectMocks
    private DeleteUserAccountUseCase deleteUserAccountUseCase;

    @Test
    void deletesUserFromCognitoAndDatabaseAfterPasswordCheck() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@neobank.mx").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        deleteUserAccountUseCase.execute(userId, "correct-password");

        verify(cognitoAdapter).deleteUser("user@neobank.mx", "correct-password");
        verify(userRepository).delete(user);
    }

    @Test
    void throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteUserAccountUseCase.execute(userId, "password"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cognitoAdapter);
    }

    @Test
    void doesNotDeleteLocallyWhenCognitoDeletionFails() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@neobank.mx").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new UnauthorizedException("INVALID_PASSWORD", "Password is incorrect"))
                .when(cognitoAdapter).deleteUser("user@neobank.mx", "wrong-password");

        assertThatThrownBy(() -> deleteUserAccountUseCase.execute(userId, "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).delete(any());
    }
}
