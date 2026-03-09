package neobank.application.usecase.account;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.UnfreezeAccountRequest;
import neobank.domain.entity.Account;
import neobank.domain.enums.AccountStatus;
import neobank.domain.repository.AccountRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.exception.BusinessException;
import neobank.infrastructure.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UnfreezeAccountUseCase {

    private final AccountRepository accountRepository;
    private final CognitoAdapter cognitoAdapter;

    @Transactional
    public void execute(UUID accountId, UUID userId, UnfreezeAccountRequest request, String email) {
        log.info("Unfreezing account: {} for user: {}", accountId, userId);

        cognitoAdapter.verifyPassword(email, request.getPassword());

        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new BusinessException("NOT_FROZEN", "Account is not frozen");
        }

        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        log.info("Account unfrozen successfully: {}", accountId);
    }
}