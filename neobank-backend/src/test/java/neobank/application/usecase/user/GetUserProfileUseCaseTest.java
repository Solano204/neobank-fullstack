package neobank.application.usecase.user;

import neobank.application.dto.response.UserProfileResponse;
import neobank.application.usecase.mapper.UserMapper;
import neobank.domain.entity.User;
import neobank.domain.repository.UserRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfileUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private GetUserProfileUseCase getUserProfileUseCase;

    @Test
    void returnsMappedProfileForUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UserProfileResponse expected = UserProfileResponse.builder().id(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(expected);

        UserProfileResponse response = getUserProfileUseCase.execute(userId);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUserProfileUseCase.execute(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
