import { api } from "./client";
import type { UserSession, FraudAlert, ApiResponse } from "@/types";

export const securityApi = {
  getSessions:       ()                                   => api.lambda.get<ApiResponse<{ sessions: UserSession[] }>>("/api/security/sessions"),
  deleteSession:     (id: string)                         => api.lambda.delete<ApiResponse>(`/api/security/sessions/${id}`),
  deleteAllSessions: ()                                   => api.lambda.post<ApiResponse>("/api/security/sessions/terminate-all"),
  enableMfa:         (method: "SMS" | "TOTP" | "EMAIL")  => api.post<ApiResponse>("/api/security/mfa/enable",  { method }),
  verifyMfa:         (code: string)                       => api.post<ApiResponse>("/api/security/mfa/verify",  { code }),
  disableMfa:        (password: string)                   => api.delete<ApiResponse>("/api/security/mfa/disable", { data: { password } }),
  getAlerts:         ()                                   => api.lambda.get<ApiResponse<{ alerts: FraudAlert[] }>>("/api/security/alerts"),
  confirmAlert:      (alertId: string)                    => api.lambda.post<ApiResponse>(`/api/security/alerts/${alertId}/confirm`),
  reportAlert:       (alertId: string, reason: string)    => api.lambda.post<ApiResponse>(`/api/security/alerts/${alertId}/report`, { reason }),
};
