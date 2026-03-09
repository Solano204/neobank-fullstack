package neobank.domain.repository;

import neobank.domain.entity.User;
import neobank.domain.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findBySessionToken(String sessionToken);
    List<UserSession> findByUser(User user);
    List<UserSession> findByUserId(UUID userId);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
    void deleteByUser(User user);
    void deleteByUserId(UUID userId);
}