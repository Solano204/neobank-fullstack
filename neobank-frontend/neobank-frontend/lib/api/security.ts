import { api } from "./client";
import type { UserSession, FraudAlert, ApiResponse } from "@/types";

interface RawSession {
  id: string;
  device: string;
  location: string;
  ip_address: string;
  last_active: string;
}

function mapSession(raw: RawSession): UserSession {
  return { id: raw.id, device: raw.device, location: raw.location, ipAddress: raw.ip_address, lastActive: raw.last_active };
}

function notImplemented(name: string): Promise<never> {
  return Promise.reject(
    new Error(`${name} has no backend yet — not implemented on the Spring backend (fraud-checker only emails an internal SNS alert today, and there's no MFA flow wired to Cognito).`)
  );
}

// SecurityController (sessions) lives on the Spring backend (EC2) and is
// backed by real persistence (SecurityService + UserSessionRepository).
// MFA and a per-user fraud-alert feed don't exist anywhere in the backend
// yet, so those are left as explicit not-implemented calls rather than
// pointed at a URL that doesn't exist.
export const securityApi = {
  getSessions: async (): Promise<ApiResponse<{ sessions: UserSession[] }>> => {
    const res = await api.get<ApiResponse<{ sessions: RawSession[] }>>("/api/security/sessions");
    return { ...res, data: { sessions: (res.data?.sessions || []).map(mapSession) } };
  },
  deleteSession:     (id: string) => api.delete<ApiResponse>(`/api/security/sessions/${id}`),
  deleteAllSessions: ()           => api.delete<ApiResponse>("/api/security/sessions/all"),

  enableMfa:  (_method: "SMS" | "TOTP" | "EMAIL") => notImplemented("enableMfa") as Promise<ApiResponse<unknown>>,
  verifyMfa:  (_code: string)                     => notImplemented("verifyMfa") as Promise<ApiResponse<unknown>>,
  disableMfa: (_password: string)                 => notImplemented("disableMfa") as Promise<ApiResponse<unknown>>,

  getAlerts:    ()                                   => notImplemented("getAlerts") as Promise<ApiResponse<{ alerts: FraudAlert[] }>>,
  confirmAlert: (_alertId: string)                   => notImplemented("confirmAlert") as Promise<ApiResponse<unknown>>,
  reportAlert:  (_alertId: string, _reason: string)  => notImplemented("reportAlert") as Promise<ApiResponse<unknown>>,
};
