import { api } from "./client";
import type { SupportTicket, ApiResponse } from "@/types";

export const supportApi = {
  sendChat:     (message: string, sessionId?: string) =>
    api.lambda.post<ApiResponse<{ message: string; sessionId: string }>>("/api/support/chat", { message, sessionId }),
  getFaq:       ()                                    => api.get<ApiResponse<{ faqs: { question: string; answer: string }[] }>>("/api/support/faq"),
  createTicket: (subject: string, description: string, priority?: string) =>
    api.lambda.post<ApiResponse<SupportTicket>>("/api/support/ticket", { subject, description, priority }),
  getTickets:   ()                                    => api.lambda.get<ApiResponse<{ tickets: SupportTicket[] }>>("/api/support/ticket"),
};
