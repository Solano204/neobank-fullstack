package neobank.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingAnalyticsResponse {
    private String period;
    private BigDecimal totalSpent;
    private BigDecimal totalReceived;
    private List<Category> categories;
    private List<MonthlyPoint> monthlyData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private String name;
        private BigDecimal amount;
        private double percentage;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPoint {
        private String month;
        private BigDecimal spent;
        private BigDecimal received;
    }
}
