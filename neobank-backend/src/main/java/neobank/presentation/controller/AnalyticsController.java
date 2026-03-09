package neobank.presentation.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    @GetMapping("/spending")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSpendingAnalytics(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                                 @RequestParam(defaultValue = "month") String period,
                                                                                 @RequestParam(required = false) String date) {
        log.info("Get spending analytics request for user: {}", userPrincipal.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("period", Map.of("start", "2026-02-01", "end", "2026-02-14"));
        response.put("total_spent", 5250.00);
        response.put("total_received", 6500.00);
        response.put("net_change", 1250.00);
        response.put("categories", List.of(
                Map.of("category", "Transfers", "amount", 3500.00, "percentage", 66.7, "transaction_count", 12),
                Map.of("category", "Withdrawals", "amount", 1750.00, "percentage", 33.3, "transaction_count", 5)
        ));
        response.put("daily_breakdown", new ArrayList<>());
        response.put("comparison_vs_last_period", Map.of("spending_change", -15.5, "trend", "DOWN"));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/balance-forecast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBalanceForecast(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get balance forecast request for user: {}", userPrincipal.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("current_balance", 1250.50);
        response.put("forecast_7_days", 1100.00);
        response.put("forecast_30_days", 950.00);
        response.put("based_on", "Average spending of last 30 days");
        response.put("confidence", 0.85);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}