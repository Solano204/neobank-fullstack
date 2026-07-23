import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { authApi } from "./auth";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

function expectBody(url: string, expected: unknown) {
  mock.onPost(url).reply((config) => {
    expect(JSON.parse(config.data || "{}")).toEqual(expected);
    return [200, { success: true }];
  });
}

describe("authApi", () => {
  it("signup POSTs the whole signup body as-is", async () => {
    const body = { email: "a@b.com", password: "P@ssw0rd1", fullName: "A B", phone: "+525512345678", country: "MX" } as never;
    expectBody("/api/auth/signup", body);
    await authApi.signup(body);
  });

  it("verifyEmail POSTs email + code", async () => {
    expectBody("/api/auth/verify-email", { email: "a@b.com", code: "123456" });
    await authApi.verifyEmail("a@b.com", "123456");
  });

  it("resendCode POSTs email", async () => {
    expectBody("/api/auth/resend-code", { email: "a@b.com" });
    await authApi.resendCode("a@b.com");
  });

  it("login POSTs the login body as-is", async () => {
    const body = { email: "a@b.com", password: "P@ssw0rd1" } as never;
    expectBody("/api/auth/login", body);
    await authApi.login(body);
  });

  it("refreshToken POSTs refreshToken", async () => {
    expectBody("/api/auth/refresh-token", { refreshToken: "r1" });
    await authApi.refreshToken("r1");
  });

  it("logout POSTs with no body", async () => {
    mock.onPost("/api/auth/logout").reply(200, { success: true });
    await authApi.logout();
  });

  it("forgotPassword POSTs email", async () => {
    expectBody("/api/auth/forgot-password", { email: "a@b.com" });
    await authApi.forgotPassword("a@b.com");
  });

  it("resetPassword POSTs email, code and newPassword", async () => {
    expectBody("/api/auth/reset-password", { email: "a@b.com", code: "123456", newPassword: "NewP@ss1" });
    await authApi.resetPassword("a@b.com", "123456", "NewP@ss1");
  });

  it("changePassword POSTs currentPassword and newPassword", async () => {
    expectBody("/api/auth/change-password", { currentPassword: "Old1!", newPassword: "New1!" });
    await authApi.changePassword("Old1!", "New1!");
  });
});
