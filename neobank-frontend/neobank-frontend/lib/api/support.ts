import { api } from "./client";
import type { SupportTicket, ApiResponse } from "@/types";

// SupportController lives on the Spring backend (EC2), not behind API
// Gateway — there is no Lambda route for this.
export const supportApi = {
  sendChat: async (message: string, sessionId?: string): Promise<ApiResponse<{ message: string; sessionId: string }>> => {
    const res = await api.post<ApiResponse<{ bot_response: string; session_id: string }>>(
      "/api/support/chat",
      { message, session_id: sessionId }
    );
    return { ...res, data: res.data && { message: res.data.bot_response, sessionId: res.data.session_id } };
  },

  getFaq: () => api.get<ApiResponse<{ categories: { id: string; name: string; questions: { id: string; question: string; answer: string }[] }[] }>>("/api/support/faq"),

  createTicket: (subject: string, description: string, priority?: string) =>
    api.post<ApiResponse<SupportTicket>>("/api/support/ticket", { subject, description, priority }),

  getTickets: () => api.get<ApiResponse<{ tickets: SupportTicket[] }>>("/api/support/tickets"),
};
