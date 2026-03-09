package neobank.application.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private AccountStatus status;
    private LocalDateTime createdAt;
}