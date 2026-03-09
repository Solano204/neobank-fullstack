package neobank.application.usecase.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.FreezeAccountRequest;
import neobank.domain.entity.Account;
import neobank.domain.enums.AccountStatus;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.BusinessException;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FreezeAccountUseCase {

    private final AccountRepository accountRepository;

    @Transactional
    public void execute(UUID accountId, UUID userId, FreezeAccountRequest request) {
        log.info("Freezing account: {} for user: {}", accountId, userId);

        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new BusinessException("ALREADY_FROZEN", "Account is already frozen");
        }

        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);

        log.info("Account frozen successfully: {}", accountId);
    }
}