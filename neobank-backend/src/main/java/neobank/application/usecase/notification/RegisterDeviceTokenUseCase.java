package neobank.application.usecase.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.domain.entity.DeviceToken;
import neobank.domain.entity.User;
import neobank.domain.repository.DeviceTokenRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterDeviceTokenUseCase {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void execute(UUID userId, String token, String platform) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).build());

        deviceToken.setUser(user);
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);

        log.info("Device token registered for user {} on platform {}", userId, platform);
    }
}
