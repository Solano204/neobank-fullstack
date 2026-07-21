import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { notificationsApi } from "./notifications";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("notificationsApi", () => {
  it("getAll GETs /api/notifications", async () => {
    mock.onGet("/api/notifications").reply(200, { data: { notifications: [] } });
    const res = await notificationsApi.getAll();
    expect(res.data.notifications).toEqual([]);
  });

  it("markRead PUTs /api/notifications/{id}/read", async () => {
    mock.onPut("/api/notifications/n1/read").reply(200, { success: true });
    await notificationsApi.markRead("n1");
  });

  it("markAllRead PUTs /api/notifications/read-all", async () => {
    mock.onPut("/api/notifications/read-all").reply(200, { success: true });
    await notificationsApi.markAllRead();
  });

  it("delete DELETEs /api/notifications/{id}", async () => {
    mock.onDelete("/api/notifications/n1").reply(200, { success: true });
    await notificationsApi.delete("n1");
  });

  // NotificationController.registerDevice() reads a raw Map<String,String>
  // body via request.get("device_token")/request.get("platform") - snake_case
  // here is intentionally correct (unlike contacts.ts's now-fixed bug, which
  // bound to a typed DTO with camelCase fields instead).
  it("registerDevice POSTs device_token/platform matching the controller's raw Map keys", async () => {
    mock.onPost("/api/notifications/register-device").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ device_token: "tok-1", platform: "ios" });
      return [200, { success: true }];
    });
    await notificationsApi.registerDevice("tok-1", "ios");
  });
});
