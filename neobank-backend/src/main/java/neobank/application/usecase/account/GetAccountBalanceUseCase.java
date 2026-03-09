package neobank.application.usecase.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountBalanceResponse;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetAccountBalanceUseCase {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountBalanceResponse execute(UUID accountId, UUID userId) {
        log.info("Fetching balance for account: {} user: {}", accountId, userId);

        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return AccountBalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}