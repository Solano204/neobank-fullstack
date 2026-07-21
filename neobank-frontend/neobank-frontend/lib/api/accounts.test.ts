import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { accountsApi } from "./accounts";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("accountsApi", () => {
  it("getAll GETs /api/accounts", async () => {
    mock.onGet("/api/accounts").reply(200, { data: { accounts: [] } });
    const res = await accountsApi.getAll();
    expect(res.data.accounts).toEqual([]);
  });

  it("getById GETs /api/accounts/{id}", async () => {
    mock.onGet("/api/accounts/acc-1").reply(200, { data: { id: "acc-1" } });
    const res = await accountsApi.getById("acc-1");
    expect(res.data.id).toBe("acc-1");
  });

  it("getBalance GETs /api/accounts/{id}/balance", async () => {
    mock.onGet("/api/accounts/acc-1/balance").reply(200, { data: { balance: 100 } });
    const res = await accountsApi.getBalance("acc-1");
    expect(res.data.balance).toBe(100);
  });

  it("freeze POSTs the reason in the body", async () => {
    mock.onPost("/api/accounts/acc-1/freeze").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ reason: "suspicious" });
      return [200, { success: true }];
    });
    await accountsApi.freeze("acc-1", "suspicious");
  });

  it("unfreeze POSTs the password in the body", async () => {
    mock.onPost("/api/accounts/acc-1/unfreeze").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ password: "secret" });
      return [200, { success: true }];
    });
    await accountsApi.unfreeze("acc-1", "secret");
  });

  it("getStatement is an explicit not-implemented stub (no backend route exists)", async () => {
    await expect(accountsApi.getStatement("acc-1")).rejects.toThrow(/no backend yet/);
  });
});
