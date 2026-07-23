package neobank.application.usecase.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.AccountResponse;
import neobank.application.service.AccountService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A lightweight keyword classifier over the same account data the app
 * already has — not a rebuild of lex-fulfillment's full Lex-driven bot
 * (that needs a real AWS Lex bot resource wired to it, which doesn't exist
 * in this Terraform yet). This at least answers with real balances instead
 * of a single hardcoded canned reply.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSupportUseCase {

    private final AccountService accountService;

    public record Result(String message, String intent, double confidence) {}

    public Result execute(UUID userId, String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "balance", "saldo", "cuanto tengo", "how much")) {
            return balanceReply(userId);
        }
        // Checked before the generic "transfer" match below -- "what's my
        // transfer limit" contains "transfer" too, and the more specific
        // intent should win.
        if (containsAny(normalized, "limit", "límite", "limite")) {
            return new Result(
                    "Your transfer limit is $50,000 MXN per transfer.",
                    "TransferLimit", 0.9);
        }
        if (containsAny(normalized, "transfer", "transferencia", "enviar dinero", "send money")) {
            return new Result(
                    "To make a transfer: open Transfer, enter the recipient's account number (CLABE), "
                            + "the amount (up to $50,000 MXN per transfer), an optional description, and confirm.",
                    "GeneralHelp", 0.7);
        }
        if (containsAny(normalized, "kyc", "verifica", "identidad", "verification")) {
            return new Result(
                    "To verify your identity: go to Profile → Verify Identity and take a clear selfie with good lighting.",
                    "GeneralHelp", 0.7);
        }
        if (containsAny(normalized, "password", "contraseña")) {
            return new Result(
                    "To change your password: Settings → Security → Change Password. Forgot it? Use 'Forgot Password' on the login screen.",
                    "GeneralHelp", 0.7);
        }

        return new Result(
                "I can help with your balance, transfers, identity verification, password resets, or transfer limits. "
                        + "For anything else, please open a support ticket and our team will follow up.",
                "FallbackIntent", 0.4);
    }

    private Result balanceReply(UUID userId) {
        List<AccountResponse> accounts = accountService.getAccounts(userId);
        if (accounts.isEmpty()) {
            return new Result("I couldn't find any accounts on your profile.", "CheckBalance", 0.5);
        }

        BigDecimal total = accounts.stream()
                .map(AccountResponse::getAvailableBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Result(
                String.format("Your available balance is $%.2f MXN across %d account(s).", total, accounts.size()),
                "CheckBalance", 0.95);
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
