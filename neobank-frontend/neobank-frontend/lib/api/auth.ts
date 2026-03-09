import { api } from "./client";
import type { SignupRequest, LoginRequest, AuthResponse, SignupResponse, ApiResponse } from "@/types";

export const authApi = {
  signup:         (body: SignupRequest)                                    => api.post<SignupResponse>("/api/auth/signup", body),
  verifyEmail:    (email: string, code: string)                           => api.post<AuthResponse>("/api/auth/verify-email", { email, code }),
  login:          (body: LoginRequest)                                     => api.post<AuthResponse>("/api/auth/login", body),
  refreshToken:   (refreshToken: string)                                  => api.post<AuthResponse>("/api/auth/refresh-token", { refreshToken }),
  logout:         ()                                                       => api.post<ApiResponse>("/api/auth/logout"),
  forgotPassword: (email: string)                                         => api.post<ApiResponse>("/api/auth/forgot-password", { email }),
  resetPassword:  (email: string, code: string, newPassword: string)      => api.post<ApiResponse>("/api/auth/reset-password", { email, code, newPassword }),
  changePassword: (currentPassword: string, newPassword: string)          => api.post<ApiResponse>("/api/auth/change-password", { currentPassword, newPassword }),
};
