package neobank.application.usecase.account;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountResponse;
import neobank.application.usecase.mapper.AccountMapper;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetAccountDetailsUseCase {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public AccountResponse execute(UUID accountId, UUID userId) {
        log.info("Fetching account details: {} for user: {}", accountId, userId);

        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return accountMapper.toResponse(account);
    }
}