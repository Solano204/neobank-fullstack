package neobank.domain.repository;

import neobank.domain.entity.Contact;
import neobank.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findByUser(User user);
    List<Contact> findByUserId(UUID userId);
    List<Contact> findByUserAndFavorite(User user, Boolean favorite);
    Optional<Contact> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserAndAccountNumber(User user, String accountNumber);
}