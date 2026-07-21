import { api } from "./client";
import type { TransferRequest, Transaction, TransactionHistory, ApiResponse } from "@/types";

// Wire shape actually returned by the transaction-service / transaction-query
// Lambdas (snake_case, epoch millis) -- kept private to this file so the rest
// of the app only ever sees the app's own camelCase Transaction type.
interface RawTransaction {
  id?: string;
  transaction_id?: string;
  from_account: string;
  to_account: string;
  amount: number;
  currency?: string;
  status: string;
  type?: string;
  description?: string;
  timestamp?: number;
  new_balance?: number;
}

export function mapTransaction(raw: RawTransaction): Transaction {
  return {
    id: raw.transaction_id || raw.id || "",
    transactionId: raw.transaction_id,
    fromAccount: raw.from_account,
    toAccount: raw.to_account,
    amount: Math.abs(raw.amount),
    currency: raw.currency || "MXN",
    status: (raw.status as Transaction["status"]) || "COMPLETED",
    type: raw.type === "TRANSFER_IN" ? "DEPOSIT" : "TRANSFER",
    description: raw.description,
    newBalance: raw.new_balance,
    createdAt: raw.timestamp ? new Date(raw.timestamp).toISOString() : new Date().toISOString(),
    timestamp: raw.timestamp,
  };
}

function notImplemented(name: string): Promise<never> {
  return Promise.reject(
    new Error(`${name} has no backend yet (no Lambda route, no API Gateway path, no Spring endpoint) — needs to be built before this can work.`)
  );
}

export const transactionsApi = {
  // Lambda endpoints (via API Gateway) — the only route that actually
  // exists is POST/GET /transactions (root, no /api prefix, no sub-paths).
  transfer: async (body: TransferRequest): Promise<ApiResponse<Transaction>> => {
    const res = await api.lambda.post<ApiResponse<RawTransaction>>("/transactions", body);
    return { ...res, data: res.data && mapTransaction(res.data) };
  },

  getHistory: async (accountNumber: string, page = 1, limit = 20): Promise<ApiResponse<TransactionHistory>> => {
    const res = await api.lambda.get<ApiResponse<{
      transactions: RawTransaction[];
      pagination: { current_page: number; per_page: number; total_count: number };
    }>>("/transactions", { params: { account: accountNumber, page, limit } });

    const raw = res.data;
    return {
      ...res,
      data: {
        transactions: (raw?.transactions || []).map(mapTransaction),
        total: raw?.pagination?.total_count ?? 0,
        page: raw?.pagination?.current_page ?? page,
        limit: raw?.pagination?.per_page ?? limit,
      },
    };
  },

  // Nothing backs these yet — no Lambda, no API Gateway route, no Spring
  // controller. Left in place (rather than deleted) so the call sites keep
  // compiling and fail with a clear message instead of a silent 403 the
  // moment someone builds the real feature and wires it up here.
  validateRecipient: (_accountNumber: string) => notImplemented("validateRecipient") as Promise<ApiResponse<{ valid: boolean; name: string }>>,
  getById:           (_id: string)             => notImplemented("getById") as Promise<ApiResponse<Transaction>>,
  getReceipt:        (_id: string)             => notImplemented("getReceipt") as Promise<ApiResponse<{ receiptUrl: string }>>,
  getScheduled:      ()                        => notImplemented("getScheduled") as Promise<ApiResponse<{ scheduled: unknown[] }>>,
  createScheduled:   (_body: object)           => notImplemented("createScheduled") as Promise<ApiResponse<unknown>>,
  cancelScheduled:   (_id: string)             => notImplemented("cancelScheduled") as Promise<ApiResponse<unknown>>,
};
