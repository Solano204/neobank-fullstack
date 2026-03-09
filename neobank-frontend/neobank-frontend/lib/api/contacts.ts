import { api } from "./client";
import type { Contact, ApiResponse } from "@/types";

export const contactsApi = {
  getAll:    ()                                          => api.get<ApiResponse<{ contacts: Contact[] }>>("/api/contacts"),
  add:       (accountNumber: string, nickname?: string) => api.post<ApiResponse<Contact>>("/api/contacts", { account_number: accountNumber, nickname }),
  remove:    (id: string)                               => api.delete<ApiResponse>(`/api/contacts/${id}`),
  toggleFav: (id: string, favorite: boolean)            => api.put<ApiResponse>(`/api/contacts/${id}/favorite`, { favorite }),
};
