import { api } from "./client";
import type { SpendingAnalytics, ApiResponse } from "@/types";

export const analyticsApi = {
  getSpending: (period?: string) =>
    api.lambda.get<ApiResponse<SpendingAnalytics>>("/api/analytics/spending", {
      params: period ? { period } : undefined,
    }),
};
