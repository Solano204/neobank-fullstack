import { api } from "./client";
import type { TransferRequest, Transaction, TransactionHistory, ApiResponse } from "@/types";

export const transactionsApi = {
  // Lambda endpoints (via API Gateway)
  transfer:          (body: TransferRequest)    => api.lambda.post<ApiResponse<Transaction>>("/api/transactions/transfer", body),
  validateRecipient: (accountNumber: string)    => api.lambda.post<ApiResponse<{ valid: boolean; name: string }>>("/api/transactions/validate-recipient", { account_number: accountNumber }),
  getHistory:        (page = 0, limit = 20)     => api.lambda.get<ApiResponse<TransactionHistory>>("/api/transactions/history", { params: { page, limit } }),
  getById:           (id: string)               => api.lambda.get<ApiResponse<Transaction>>(`/api/transactions/${id}`),
  getReceipt:        (id: string)               => api.lambda.get<ApiResponse<{ receiptUrl: string }>>(`/api/transactions/${id}/receipt`),
  getScheduled:      ()                         => api.lambda.get<ApiResponse<{ scheduled: unknown[] }>>("/api/transactions/scheduled"),
  createScheduled:   (body: object)             => api.lambda.post<ApiResponse>("/api/transactions/scheduled", body),
  cancelScheduled:   (id: string)               => api.lambda.delete<ApiResponse>(`/api/transactions/scheduled/${id}`),
};
