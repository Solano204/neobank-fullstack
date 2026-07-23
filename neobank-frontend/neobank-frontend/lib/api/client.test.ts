import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import axios from "axios";

// The refresh call in client.ts uses the raw `axios` module directly (not
// the apiClient instance, deliberately - it must not go through apiClient's
// own request/response interceptors, or a 401 on the refresh call itself
// would recurse). Mocking it needs its own MockAdapter on that same raw
// default export, separate from the one on apiClient/lambdaClient.
const rawAxiosMock = new MockAdapter(axios);

// tokenStorage/apiClient/api are all created once at module load - re-import
// fresh per test via vi.resetModules() so localStorage state set up in one
// test can't leak into the next through a cached module instance.
async function freshClient() {
  vi.resetModules();
  return await import("./client");
}

const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => {
      store[k] = v;
    },
    removeItem: (k: string) => {
      delete store[k];
    },
    clear: () => {
      store = {};
    },
  };
})();

beforeEach(() => {
  localStorageMock.clear();
  vi.stubGlobal("localStorage", localStorageMock);
  // jsdom isn't loaded for this node-environment file - window is provided
  // just enough for tokenStorage's `typeof window !== "undefined"` checks
  // and the interceptor's redirect-on-refresh-failure branch.
  vi.stubGlobal("window", { localStorage: localStorageMock, location: { href: "" } });
  rawAxiosMock.reset();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("tokenStorage", () => {
  it("set() stores both tokens, get()/getRefresh() read them back", async () => {
    const { tokenStorage } = await freshClient();
    tokenStorage.set("access-1", "refresh-1");
    expect(tokenStorage.get()).toBe("access-1");
    expect(tokenStorage.getRefresh()).toBe("refresh-1");
  });

  it("clear() removes tokens and the cached user", async () => {
    const { tokenStorage } = await freshClient();
    tokenStorage.set("access-1", "refresh-1");
    tokenStorage.setUser({ id: "u1" });
    tokenStorage.clear();
    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getRefresh()).toBeNull();
    expect(tokenStorage.getUser()).toBeNull();
  });

  it("setUser()/getUser() round-trip through JSON", async () => {
    const { tokenStorage } = await freshClient();
    tokenStorage.setUser({ id: "u1", email: "a@b.com" });
    expect(tokenStorage.getUser()).toEqual({ id: "u1", email: "a@b.com" });
  });

  it("getUser() returns null when nothing is stored", async () => {
    const { tokenStorage } = await freshClient();
    expect(tokenStorage.getUser()).toBeNull();
  });
});

describe("apiClient request interceptor", () => {
  it("attaches the stored access token as a Bearer header", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("my-access-token", "my-refresh-token");
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/accounts").reply((config) => {
      expect(config.headers?.Authorization).toBe("Bearer my-access-token");
      return [200, { data: [] }];
    });

    await api.get("/api/accounts");
    mock.restore();
  });

  it("sends no Authorization header when there is no stored token", async () => {
    const { apiClient, api } = await freshClient();
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/accounts").reply((config) => {
      expect(config.headers?.Authorization).toBeUndefined();
      return [200, { data: [] }];
    });

    await api.get("/api/accounts");
    mock.restore();
  });
});

describe("apiClient response interceptor - 401 refresh flow", () => {
  it("on 401 with a refresh token, refreshes and retries the original request once", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("expired-access", "valid-refresh");
    const mock = new MockAdapter(apiClient);

    mock.onGet("/api/accounts").replyOnce(401);
    rawAxiosMock.onPost("http://localhost:8080/api/auth/refresh-token").reply(200, {
      data: { accessToken: "new-access", refreshToken: "new-refresh" },
    });
    mock.onGet("/api/accounts").reply((config) => {
      expect(config.headers?.Authorization).toBe("Bearer new-access");
      return [200, { data: ["account-1"] }];
    });

    const result = await api.get("/api/accounts");

    expect(result).toEqual({ data: ["account-1"] });
    expect(tokenStorage.get()).toBe("new-access");
    expect(tokenStorage.getRefresh()).toBe("new-refresh");
    mock.restore();
  });

  it("on 401 with no refresh token available, clears storage and redirects to login without calling refresh", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("expired-access", ""); // no real refresh token
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/accounts").reply(401);

    await expect(api.get("/api/accounts")).rejects.toBeTruthy();

    expect(tokenStorage.get()).toBeNull();
    expect((window as unknown as { location: { href: string } }).location.href).toBe("/auth/login");
    mock.restore();
  });

  it("when the refresh call itself fails, clears storage and redirects to login", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("expired-access", "stale-refresh");
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/accounts").reply(401);
    rawAxiosMock.onPost("http://localhost:8080/api/auth/refresh-token").reply(401);

    await expect(api.get("/api/accounts")).rejects.toBeTruthy();

    expect(tokenStorage.get()).toBeNull();
    expect((window as unknown as { location: { href: string } }).location.href).toBe("/auth/login");
    mock.restore();
  });

  it("does not attempt a second refresh for a request that already retried once", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("expired-access", "valid-refresh");
    const mock = new MockAdapter(apiClient);
    let refreshCalls = 0;

    mock.onGet("/api/accounts").reply(401); // always 401, even after "refresh"
    rawAxiosMock.onPost("http://localhost:8080/api/auth/refresh-token").reply(() => {
      refreshCalls += 1;
      return [200, { data: { accessToken: "new-access", refreshToken: "new-refresh" } }];
    });

    await expect(api.get("/api/accounts")).rejects.toBeTruthy();
    expect(refreshCalls).toBe(1); // not retried in an infinite loop
    mock.restore();
  });

  it("a non-401 error is rejected without touching tokens", async () => {
    const { tokenStorage, apiClient, api } = await freshClient();
    tokenStorage.set("access-1", "refresh-1");
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/accounts").reply(500, { message: "Internal error" });

    await expect(api.get("/api/accounts")).rejects.toBeTruthy();
    expect(tokenStorage.get()).toBe("access-1"); // untouched
    mock.restore();
  });
});

describe("getErrorMessage", () => {
  it("extracts the server's message from an Axios error response", async () => {
    const { getErrorMessage, apiClient, api } = await freshClient();
    const mock = new MockAdapter(apiClient);
    mock.onGet("/api/x").reply(400, { message: "Email already registered" });

    try {
      await api.get("/api/x");
      throw new Error("should have thrown");
    } catch (e) {
      expect(getErrorMessage(e)).toBe("Email already registered");
    }
    mock.restore();
  });

  it("falls back to a generic Spanish message for a non-Axios, non-Error value", async () => {
    const { getErrorMessage } = await freshClient();
    expect(getErrorMessage("just a string")).toBe("Error desconocido");
  });

  it("uses a plain Error's own message", async () => {
    const { getErrorMessage } = await freshClient();
    expect(getErrorMessage(new Error("boom"))).toBe("boom");
  });
});
