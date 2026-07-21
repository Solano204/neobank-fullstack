import { api } from "./client";
import type { Account, AccountBalance, ApiResponse } from "@/types";

function notImplemented(name: string): Promise<never> {
  return Promise.reject(
    new Error(`${name} has no backend yet (no /api/accounts/{id}/statement route on AccountController) — needs to be built before this can work.`)
  );
}

export const accountsApi = {
  getAll:     ()               => api.get<ApiResponse<{ accounts: Account[] }>>("/api/accounts"),
  getById:    (id: string)     => api.get<ApiResponse<Account>>(`/api/accounts/${id}`),
  getBalance: (id: string)     => api.get<ApiResponse<AccountBalance>>(`/api/accounts/${id}/balance`),
  freeze:     (id: string, reason: string)   => api.post<ApiResponse>(`/api/accounts/${id}/freeze`,   { reason }),
  unfreeze:   (id: string, password: string) => api.post<ApiResponse>(`/api/accounts/${id}/unfreeze`, { password }),

  // Nothing backs this yet — no AccountController route for a statement/export.
  // Left as an explicit stub (rather than deleted) so any future call site
  // fails with a clear message instead of a silent 404.
  getStatement: (_id: string, _from?: string, _to?: string) =>
    notImplemented("getStatement") as Promise<ApiResponse<{ transactions: unknown[] }>>,
};
