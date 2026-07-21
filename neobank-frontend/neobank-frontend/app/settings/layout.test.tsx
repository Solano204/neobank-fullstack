// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import RouteLayout from "./layout";
import { useAuthStore } from "@/lib/store/authStore";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }), usePathname: () => "/settings" }));

beforeEach(() => {
  push.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("RouteLayout (protected shell shared across authenticated routes)", () => {
  it("shows a spinner and no children while auth state is loading", () => {
    const initFromStorage = vi.fn();
    useAuthStore.setState({ isLoading: true, isLoggedIn: false, user: null, initFromStorage });
    render(<RouteLayout><div>Page content</div></RouteLayout>);

    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(screen.queryByText("Page content")).not.toBeInTheDocument();
    expect(initFromStorage).toHaveBeenCalled();
  });

  it("redirects to /auth/login and renders nothing once loading finishes logged out", () => {
    useAuthStore.setState({ isLoading: false, isLoggedIn: false, user: null, initFromStorage: vi.fn() });
    const { container } = render(<RouteLayout><div>Page content</div></RouteLayout>);

    expect(push).toHaveBeenCalledWith("/auth/login");
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the sidebar and children once loaded and logged in", () => {
    useAuthStore.setState({
      isLoading: false, isLoggedIn: true,
      user: { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos Perez", kycStatus: "APPROVED" } as never,
      initFromStorage: vi.fn(),
    });
    render(<RouteLayout><div>Page content</div></RouteLayout>);

    expect(screen.getByText("NeoBank")).toBeInTheDocument(); // Sidebar logo
    expect(screen.getByText("Page content")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
