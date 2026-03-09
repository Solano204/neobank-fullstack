package neobank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.FreezeAccountRequest;
import neobank.application.dto.request.UnfreezeAccountRequest;
import neobank.application.dto.response.AccountBalanceResponse;
import neobank.application.dto.response.AccountResponse;
import neobank.application.usecase.account.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final GetAccountsUseCase getAccountsUseCase;
    private final GetAccountDetailsUseCase getAccountDetailsUseCase;
    private final GetAccountBalanceUseCase getAccountBalanceUseCase;
    private final FreezeAccountUseCase freezeAccountUseCase;
    private final UnfreezeAccountUseCase unfreezeAccountUseCase;

    public List<AccountResponse> getAccounts(UUID userId) {
        return getAccountsUseCase.execute(userId);
    }

    public AccountResponse getAccountDetails(UUID accountId, UUID userId) {
        return getAccountDetailsUseCase.execute(accountId, userId);
    }

    public AccountBalanceResponse getAccountBalance(UUID accountId, UUID userId) {
        return getAccountBalanceUseCase.execute(accountId, userId);
    }

    public void freezeAccount(UUID accountId, UUID userId, FreezeAccountRequest request) {
        freezeAccountUseCase.execute(accountId, userId, request);
    }

    public void unfreezeAccount(UUID accountId, UUID userId, UnfreezeAccountRequest request, String email) {
        unfreezeAccountUseCase.execute(accountId, userId, request, email);
    }
}