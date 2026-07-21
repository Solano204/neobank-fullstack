// @vitest-environment jsdom
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { useAuthStore } from "./authStore";
import { tokenStorage } from "@/lib/api/client";

const user = { id: "u1", email: "a@b.com", fullName: "Test User" } as never;

beforeEach(() => {
  localStorage.clear();
  useAuthStore.setState({ user: null, isLoading: true, isLoggedIn: false });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useAuthStore", () => {
  it("setUser stores the user in localStorage and flips isLoggedIn/isLoading", () => {
    useAuthStore.getState().setUser(user);

    const state = useAuthStore.getState();
    expect(state.user).toEqual(user);
    expect(state.isLoggedIn).toBe(true);
    expect(state.isLoading).toBe(false);
    expect(tokenStorage.getUser()).toEqual(user);
  });

  it("setTokens persists both tokens without changing store state", () => {
    useAuthStore.getState().setTokens("access-1", "refresh-1");

    expect(tokenStorage.get()).toBe("access-1");
    expect(tokenStorage.getRefresh()).toBe("refresh-1");
    expect(useAuthStore.getState().user).toBeNull(); // unaffected
  });

  it("logout clears storage, resets state, and redirects to login", () => {
    useAuthStore.getState().setUser(user);
    useAuthStore.getState().setTokens("access-1", "refresh-1");
    // jsdom throws "Not implemented: navigation" on a real assignment to
    // window.location.href - stub it so the redirect itself is just observed.
    delete (window as unknown as { location?: unknown }).location;
    (window as unknown as { location: { href: string } }).location = { href: "" };

    useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.isLoggedIn).toBe(false);
    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getUser()).toBeNull();
    expect(window.location.href).toBe("/auth/login");
  });

  describe("initFromStorage", () => {
    it("restores the session when both a token and a user are already stored", () => {
      tokenStorage.set("access-1", "refresh-1");
      tokenStorage.setUser(user);

      useAuthStore.getState().initFromStorage();

      const state = useAuthStore.getState();
      expect(state.user).toEqual(user);
      expect(state.isLoggedIn).toBe(true);
      expect(state.isLoading).toBe(false);
    });

    it("just stops loading (stays logged out) when nothing is stored", () => {
      useAuthStore.getState().initFromStorage();

      const state = useAuthStore.getState();
      expect(state.isLoggedIn).toBe(false);
      expect(state.isLoading).toBe(false);
    });

    it("stays logged out when a token exists but no cached user does", () => {
      tokenStorage.set("access-1", "refresh-1");

      useAuthStore.getState().initFromStorage();

      expect(useAuthStore.getState().isLoggedIn).toBe(false);
    });
  });
});
