import { describe, it, expect, vi, beforeEach } from "vitest";

const { redirect } = vi.hoisted(() => ({ redirect: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect }));

import RootPage from "./page";

beforeEach(() => {
  redirect.mockReset();
});

describe("RootPage", () => {
  it("redirects to /dashboard", () => {
    RootPage();
    expect(redirect).toHaveBeenCalledWith("/dashboard");
  });
});
