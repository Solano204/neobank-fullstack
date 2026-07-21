import { api } from "./client";
import type { Contact, ApiResponse } from "@/types";

export const contactsApi = {
  getAll:    ()                                          => api.get<ApiResponse<{ contacts: Contact[] }>>("/api/contacts"),
  // AddContactRequest binds `accountNumber` via plain Jackson defaults (no
  // snake_case naming strategy configured anywhere in the backend - checked
  // application.yml and every @Bean ObjectMapper) - sending `account_number`
  // left it null on the server, so @NotBlank rejected every real "add
  // contact" call with a 400. Confirmed against ContactControllerIT, which
  // posts `accountNumber` camelCase and asserts 201.
  add:       (accountNumber: string, nickname?: string) => api.post<ApiResponse<Contact>>("/api/contacts", { accountNumber, nickname }),
  remove:    (id: string)                               => api.delete<ApiResponse>(`/api/contacts/${id}`),
  toggleFav: (id: string, favorite: boolean)            => api.put<ApiResponse>(`/api/contacts/${id}/favorite`, { favorite }),
};
