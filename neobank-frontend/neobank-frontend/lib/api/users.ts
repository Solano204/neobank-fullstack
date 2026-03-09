import { api } from "./client";
import type { User, UserSettings, ApiResponse } from "@/types";

export const usersApi = {
  getProfile:     ()                             => api.get<ApiResponse<User>>("/api/users/profile"),
  updateProfile:  (body: Partial<User>)          => api.put<ApiResponse<User>>("/api/users/profile", body),
  getSettings:    ()                             => api.get<ApiResponse<UserSettings>>("/api/users/settings"),
  updateSettings: (body: Partial<UserSettings>)  => api.put<ApiResponse<UserSettings>>("/api/users/settings", body),
};
