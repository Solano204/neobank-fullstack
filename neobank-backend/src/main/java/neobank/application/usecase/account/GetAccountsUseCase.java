package neobank.application.usecase.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountResponse;
import neobank.application.usecase.mapper.AccountMapper;
import neobank.domain.entity.Account;
import neobank.domain.repository.AccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetAccountsUseCase {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public List<AccountResponse> execute(UUID userId) {
        log.info("Fetching accounts for user: {}", userId);

        List<Account> accounts = accountRepository.findByUserId(userId);

        return accounts.stream()
                .map(accountMapper::toResponse)
                .toList();
    }
}