package neobank.domain.repository;

import neobank.domain.entity.Account;
import neobank.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUser(User user);
    List<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByAccountNumber(String accountNumber);
}