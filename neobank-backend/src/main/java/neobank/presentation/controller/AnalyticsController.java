package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.application.dto.response.SpendingAnalyticsResponse;
import neobank.application.usecase.analytics.GetBalanceForecastUseCase;
import neobank.application.usecase.analytics.GetSpendingAnalyticsUseCase;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final GetSpendingAnalyticsUseCase getSpendingAnalyticsUseCase;
    private final GetBalanceForecastUseCase getBalanceForecastUseCase;

    @GetMapping("/spending")
    public ResponseEntity<ApiResponse<SpendingAnalyticsResponse>> getSpendingAnalytics(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                                 @RequestParam(defaultValue = "month") String period) {
        log.info("Get spending analytics request for user: {}", userPrincipal.getId());

        SpendingAnalyticsResponse response = getSpendingAnalyticsUseCase.execute(userPrincipal.getId(), period);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/balance-forecast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBalanceForecast(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get balance forecast request for user: {}", userPrincipal.getId());

        Map<String, Object> response = getBalanceForecastUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
