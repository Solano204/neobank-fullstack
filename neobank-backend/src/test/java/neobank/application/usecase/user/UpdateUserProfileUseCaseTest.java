package neobank.application.usecase.user;

import neobank.application.dto.request.UpdateProfileRequest;
import neobank.application.dto.response.UserProfileResponse;
import neobank.application.usecase.mapper.UserMapper;
import neobank.domain.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UpdateUserProfileUseCase updateUserProfileUseCase;

    @Test
    void updatesOnlyProvidedFieldsIncludingAddress() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).fullName("Old Name").phone("+525500000000").city("Old City").build();
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New Name")
                .address(UpdateProfileRequest.AddressDto.builder().city("New City").build())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(User.class))).thenReturn(UserProfileResponse.builder().id(userId).build());

        updateUserProfileUseCase.execute(userId, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("New Name");
        assertThat(captor.getValue().getPhone()).isEqualTo("+525500000000");
        assertThat(captor.getValue().getCity()).isEqualTo("New City");
    }

    @Test
    void throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUserProfileUseCase.execute(userId, UpdateProfileRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
