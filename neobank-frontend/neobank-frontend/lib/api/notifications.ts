import { api } from "./client";
import type { Notification, ApiResponse } from "@/types";

export const notificationsApi = {
  getAll:         ()              => api.lambda.get<ApiResponse<{ notifications: Notification[] }>>("/api/notifications"),
  markRead:       (id: string)    => api.lambda.post<ApiResponse>(`/api/notifications/${id}/read`),
  markAllRead:    ()              => api.lambda.post<ApiResponse>("/api/notifications/read-all"),
  registerDevice: (token: string, platform: string) =>
    api.lambda.post<ApiResponse>("/api/notifications/register-device", { token, platform }),
};
