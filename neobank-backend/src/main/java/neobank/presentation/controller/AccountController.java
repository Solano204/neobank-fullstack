package neobank.presentation.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.request.FreezeAccountRequest;
import neobank.application.dto.request.UnfreezeAccountRequest;
import neobank.application.dto.response.AccountBalanceResponse;
import neobank.application.dto.response.AccountResponse;
import neobank.application.dto.response.ApiResponse;
import neobank.application.usecase.account.*;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final GetAccountsUseCase getAccountsUseCase;
    private final GetAccountDetailsUseCase getAccountDetailsUseCase;
    private final GetAccountBalanceUseCase getAccountBalanceUseCase;
    private final FreezeAccountUseCase freezeAccountUseCase;
    private final UnfreezeAccountUseCase unfreezeAccountUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccounts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get accounts request for user: {}", userPrincipal.getId());

        List<AccountResponse> accounts = getAccountsUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountDetails(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                          @PathVariable UUID accountId) {
        log.info("Get account details request for account: {} user: {}", accountId, userPrincipal.getId());

        AccountResponse account = getAccountDetailsUseCase.execute(accountId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<AccountBalanceResponse>> getAccountBalance(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                                 @PathVariable UUID accountId) {
        log.info("Get balance request for account: {} user: {}", accountId, userPrincipal.getId());

        AccountBalanceResponse balance = getAccountBalanceUseCase.execute(accountId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    @PostMapping("/{accountId}/freeze")
    public ResponseEntity<ApiResponse<String>> freezeAccount(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                             @PathVariable UUID accountId,
                                                             @Valid @RequestBody FreezeAccountRequest request) {
        log.info("Freeze account request for account: {} user: {}", accountId, userPrincipal.getId());

        freezeAccountUseCase.execute(accountId, userPrincipal.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Account frozen successfully"));
    }

    @PostMapping("/{accountId}/unfreeze")
    public ResponseEntity<ApiResponse<String>> unfreezeAccount(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                               @PathVariable UUID accountId,
                                                               @Valid @RequestBody UnfreezeAccountRequest request) {
        log.info("Unfreeze account request for account: {} user: {}", accountId, userPrincipal.getId());

        unfreezeAccountUseCase.execute(accountId, userPrincipal.getId(), request, userPrincipal.getEmail());

        return ResponseEntity.ok(ApiResponse.success("Account unfrozen successfully"));
    }
}