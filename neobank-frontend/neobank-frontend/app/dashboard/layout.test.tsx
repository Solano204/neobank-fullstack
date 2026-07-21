// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import DashboardLayout from "./layout";
import { useAuthStore } from "@/lib/store/authStore";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }), usePathname: () => "/dashboard" }));

beforeEach(() => {
  push.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("DashboardLayout", () => {
  it('shows the "Cargando NeoBank…" loading state and no children while auth state is loading', () => {
    const initFromStorage = vi.fn();
    useAuthStore.setState({ isLoading: true, isLoggedIn: false, user: null, initFromStorage });
    render(<DashboardLayout><div>Page content</div></DashboardLayout>);

    expect(screen.getByText("Cargando NeoBank…")).toBeInTheDocument();
    expect(screen.queryByText("Page content")).not.toBeInTheDocument();
    expect(initFromStorage).toHaveBeenCalled();
  });

  it("redirects to /auth/login and renders nothing once loading finishes logged out", () => {
    useAuthStore.setState({ isLoading: false, isLoggedIn: false, user: null, initFromStorage: vi.fn() });
    const { container } = render(<DashboardLayout><div>Page content</div></DashboardLayout>);

    expect(push).toHaveBeenCalledWith("/auth/login");
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the sidebar and children once loaded and logged in", () => {
    useAuthStore.setState({
      isLoading: false, isLoggedIn: true,
      user: { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos Perez", kycStatus: "APPROVED" } as never,
      initFromStorage: vi.fn(),
    });
    render(<DashboardLayout><div>Page content</div></DashboardLayout>);

    expect(screen.getByText("NeoBank")).toBeInTheDocument(); // Sidebar logo
    expect(screen.getByText("Page content")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
