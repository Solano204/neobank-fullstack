import { api } from "./client";
import type { Account, AccountBalance, ApiResponse } from "@/types";

export const accountsApi = {
  getAll:       ()                                     => api.get<ApiResponse<{ accounts: Account[] }>>("/api/accounts"),
  getById:      (id: string)                           => api.get<ApiResponse<Account>>(`/api/accounts/${id}`),
  getBalance:   (id: string)                           => api.get<ApiResponse<AccountBalance>>(`/api/accounts/${id}/balance`),
  getStatement: (id: string, from?: string, to?: string) =>
    api.get<ApiResponse<{ transactions: unknown[] }>>(`/api/accounts/${id}/statement`, {
      params: { from, to },
    }),
  freeze:       (id: string, reason: string)           => api.post<ApiResponse>(`/api/accounts/${id}/freeze`,   { reason }),
  unfreeze:     (id: string, password: string)         => api.post<ApiResponse>(`/api/accounts/${id}/unfreeze`, { password }),
};
