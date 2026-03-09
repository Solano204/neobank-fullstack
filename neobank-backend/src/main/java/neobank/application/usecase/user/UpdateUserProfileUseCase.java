package neobank.application.usecase.user;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.UpdateProfileRequest;
import neobank.application.dto.response.UserProfileResponse;
import neobank.application.usecase.mapper.UserMapper;
import neobank.domain.entity.User;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateUserProfileUseCase {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserProfileResponse execute(UUID userId, UpdateProfileRequest request) {
        log.info("Updating profile for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getAddress() != null) {
            updateAddress(user, request.getAddress());
        }

        User updatedUser = userRepository.save(user);

        log.info("Profile updated successfully for user: {}", userId);

        return userMapper.toProfileResponse(updatedUser);
    }

    private void updateAddress(User user, UpdateProfileRequest.AddressDto address) {
        if (address.getStreet() != null) {
            user.setStreet(address.getStreet());
        }
        if (address.getCity() != null) {
            user.setCity(address.getCity());
        }
        if (address.getState() != null) {
            user.setState(address.getState());
        }
        if (address.getPostalCode() != null) {
            user.setPostalCode(address.getPostalCode());
        }
        if (address.getCountry() != null) {
            user.setCountry(address.getCountry());
        }
    }
}