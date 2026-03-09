package neobank.domain.repository;

import neobank.domain.entity.User;
import neobank.domain.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByUser(User user);
    Optional<UserSettings> findByUserId(UUID userId);
}