import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { securityApi } from "./security";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("securityApi", () => {
  it("getSessions maps the backend's snake_case session shape to the app's camelCase UserSession", async () => {
    mock.onGet("/api/security/sessions").reply(200, {
      data: {
        sessions: [
          { id: "s1", device: "iPhone 15", location: "Unknown", ip_address: "203.0.113.9", last_active: "2026-01-01T10:00:00Z" },
        ],
      },
    });

    const res = await securityApi.getSessions();

    expect(res.data.sessions).toEqual([
      { id: "s1", device: "iPhone 15", location: "Unknown", ipAddress: "203.0.113.9", lastActive: "2026-01-01T10:00:00Z" },
    ]);
  });

  it("getSessions handles a missing sessions array without throwing", async () => {
    mock.onGet("/api/security/sessions").reply(200, { data: {} });
    const res = await securityApi.getSessions();
    expect(res.data.sessions).toEqual([]);
  });

  it("deleteSession DELETEs /api/security/sessions/{id}", async () => {
    mock.onDelete("/api/security/sessions/s1").reply(200, { success: true });
    await securityApi.deleteSession("s1");
  });

  it("deleteAllSessions DELETEs /api/security/sessions/all", async () => {
    mock.onDelete("/api/security/sessions/all").reply(200, { success: true });
    await securityApi.deleteAllSessions();
  });

  // No MFA flow or per-user fraud-alert feed exists on the backend yet -
  // these must fail loudly (a clear message) rather than silently 404 or
  // hit a URL that was never built.
  it.each([
    ["enableMfa", () => securityApi.enableMfa("SMS")],
    ["verifyMfa", () => securityApi.verifyMfa("123456")],
    ["disableMfa", () => securityApi.disableMfa("password")],
    ["getAlerts", () => securityApi.getAlerts()],
    ["confirmAlert", () => securityApi.confirmAlert("a1")],
    ["reportAlert", () => securityApi.reportAlert("a1", "not me")],
  ])("%s is an explicit not-implemented stub", async (_name, call) => {
    await expect(call()).rejects.toThrow(/has no backend yet/);
  });
});
