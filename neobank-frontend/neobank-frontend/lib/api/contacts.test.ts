import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { contactsApi } from "./contacts";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("contactsApi", () => {
  it("getAll GETs /api/contacts", async () => {
    mock.onGet("/api/contacts").reply(200, { data: { contacts: [] } });
    const res = await contactsApi.getAll();
    expect(res.data.contacts).toEqual([]);
  });

  // The backend's AddContactRequest binds to `accountNumber` (camelCase, per
  // ContactControllerIT), not `account_number` - this wire shape is worth
  // pinning explicitly given the sibling kyc.ts/security.ts files each found
  // a real snake_case/camelCase mismatch bug against the real backend.
  it("add POSTs accountNumber and nickname camelCase (matching AddContactRequest)", async () => {
    mock.onPost("/api/contacts").reply((config) => {
      const body = JSON.parse(config.data);
      expect(body).toEqual({ accountNumber: "123412341234123412", nickname: "Mom" });
      return [201, { data: { id: "c1" } }];
    });
    await contactsApi.add("123412341234123412", "Mom");
  });

  it("remove DELETEs /api/contacts/{id}", async () => {
    mock.onDelete("/api/contacts/c1").reply(200, { success: true });
    await contactsApi.remove("c1");
  });

  it("toggleFav PUTs the favorite flag", async () => {
    mock.onPut("/api/contacts/c1/favorite").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ favorite: true });
      return [200, { success: true }];
    });
    await contactsApi.toggleFav("c1", true);
  });
});
