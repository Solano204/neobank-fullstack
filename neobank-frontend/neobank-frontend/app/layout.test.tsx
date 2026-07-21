// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import RootLayout, { metadata } from "./layout";

// next/font/google relies on the Next.js SWC compiler to rewrite the call
// into a static font-loading manifest; outside that pipeline (plain Vite/
// Vitest) calling it throws, so it's stubbed here purely to make the module
// importable for testing this layout's own JSX/metadata.
vi.mock("next/font/google", () => ({
  Syne:    () => ({ variable: "--font-syne" }),
  DM_Sans: () => ({ variable: "--font-dm-sans" }),
}));

describe("RootLayout", () => {
  it("renders its children and the toast container", () => {
    render(<RootLayout><div>App content</div></RootLayout>);
    expect(screen.getByText("App content")).toBeInTheDocument();
  });

  it("exposes the expected page metadata", () => {
    expect(metadata.title).toBe("NeoBank — Banca digital moderna");
    expect(metadata.description).toMatch(/transferencias instantáneas/);
  });
});
