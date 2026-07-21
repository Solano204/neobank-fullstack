import { api } from "./client";
import type { SpendingAnalytics, ApiResponse } from "@/types";

// AnalyticsController lives on the Spring backend (EC2), not behind API
// Gateway — there is no Lambda route for this.
export const analyticsApi = {
  getSpending: (period?: string) =>
    api.get<ApiResponse<SpendingAnalytics>>("/api/analytics/spending", {
      params: period ? { period } : undefined,
    }),
  getBalanceForecast: () =>
    api.get<ApiResponse<Record<string, unknown>>>("/api/analytics/balance-forecast"),
};
