import { api } from "./client";
import type { Notification, ApiResponse } from "@/types";

// NotificationController lives on the Spring backend (EC2), not behind API
// Gateway — there is no Lambda route for this.
export const notificationsApi = {
  getAll:         ()              => api.get<ApiResponse<{ notifications: Notification[] }>>("/api/notifications"),
  markRead:       (id: string)    => api.put<ApiResponse>(`/api/notifications/${id}/read`),
  markAllRead:    ()              => api.put<ApiResponse>("/api/notifications/read-all"),
  delete:         (id: string)    => api.delete<ApiResponse>(`/api/notifications/${id}`),
  registerDevice: (token: string, platform: string) =>
    api.post<ApiResponse>("/api/notifications/register-device", { device_token: token, platform }),
};
