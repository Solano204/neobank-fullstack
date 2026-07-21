// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import Header from "./Header";
import { useAuthStore } from "@/lib/store/authStore";

beforeEach(() => {
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

describe("Header", () => {
  it("renders the page title", () => {
    render(<Header title="Dashboard" />);
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
  });

  it("shows a '?' avatar placeholder when there is no logged-in user", () => {
    render(<Header title="Dashboard" />);
    expect(screen.getByText("?")).toBeInTheDocument();
  });

  it("shows the logged-in user's initials in the avatar", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", fullName: "Carlos Perez", kycStatus: "PENDING" } as never });
    render(<Header title="Dashboard" />);
    expect(screen.getByText("CP")).toBeInTheDocument();
  });

  it("links the bell icon to /notifications and the avatar to /settings", () => {
    render(<Header title="Dashboard" />);
    const hrefs = screen.getAllByRole("link").map((a) => a.getAttribute("href"));
    expect(hrefs).toEqual(["/notifications", "/settings"]);
  });
});
