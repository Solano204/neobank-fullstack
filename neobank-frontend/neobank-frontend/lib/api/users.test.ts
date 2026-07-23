import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { usersApi } from "./users";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("usersApi", () => {
  it("getProfile GETs /api/users/profile", async () => {
    mock.onGet("/api/users/profile").reply(200, { data: { id: "u1" } });
    const res = await usersApi.getProfile();
    expect(res.data.id).toBe("u1");
  });

  it("updateProfile PUTs the partial body as-is", async () => {
    mock.onPut("/api/users/profile").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ fullName: "New Name" });
      return [200, { data: { id: "u1", fullName: "New Name" } }];
    });
    await usersApi.updateProfile({ fullName: "New Name" });
  });

  it("getSettings GETs /api/users/settings", async () => {
    mock.onGet("/api/users/settings").reply(200, { data: { language: "es" } });
    const res = await usersApi.getSettings();
    expect(res.data.language).toBe("es");
  });

  it("updateSettings PUTs the partial body as-is", async () => {
    mock.onPut("/api/users/settings").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ theme: "dark" });
      return [200, { data: { theme: "dark" } }];
    });
    await usersApi.updateSettings({ theme: "dark" });
  });

  // UserController.deleteAccount() reads password as @RequestParam (query
  // string), not a JSON body.
  it("deleteAccount sends password as a query param, not a body", async () => {
    mock.onDelete("/api/users/account").reply((config) => {
      expect(config.params).toEqual({ password: "current-pass" });
      expect(config.data).toBeUndefined();
      return [200, { success: true }];
    });
    await usersApi.deleteAccount("current-pass");
  });
});
