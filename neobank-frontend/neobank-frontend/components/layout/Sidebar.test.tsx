// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Sidebar from "./Sidebar";
import { useAuthStore } from "@/lib/store/authStore";

const { pathname } = vi.hoisted(() => ({ pathname: { current: "/dashboard" } }));
vi.mock("next/navigation", () => ({
  usePathname: () => pathname.current,
}));

beforeEach(() => {
  pathname.current = "/dashboard";
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

describe("Sidebar", () => {
  it("renders every nav item's label", () => {
    render(<Sidebar />);
    ["Dashboard", "Cuentas", "Movimientos", "Transferir", "Verificación", "Notificaciones", "Contactos", "Análisis", "Soporte", "Seguridad", "Ajustes"].forEach((label) => {
      expect(screen.getByText(label)).toBeInTheDocument();
    });
  });

  it("marks the current route's nav item as active", () => {
    pathname.current = "/accounts";
    render(<Sidebar />);
    expect(screen.getByText("Cuentas").closest("a")).toHaveClass("active");
    expect(screen.getByText("Dashboard").closest("a")).not.toHaveClass("active");
  });

  it("hides the user section entirely when logged out", () => {
    render(<Sidebar />);
    expect(screen.queryByText("Cerrar sesión")).not.toBeInTheDocument();
  });

  it("shows the user's name, email and initials when logged in", () => {
    useAuthStore.setState({ user: { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos Perez", kycStatus: "PENDING" } as never });
    render(<Sidebar />);
    expect(screen.getByText("Carlos Perez")).toBeInTheDocument();
    expect(screen.getByText("carlos@neobank.mx")).toBeInTheDocument();
    expect(screen.getByText("CP")).toBeInTheDocument();
  });

  it("clicking 'Cerrar sesión' calls logout", () => {
    const logout = vi.fn();
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", fullName: "A B", kycStatus: "PENDING" } as never, logout });
    render(<Sidebar />);
    fireEvent.click(screen.getByText("Cerrar sesión"));
    expect(logout).toHaveBeenCalledTimes(1);
  });
});
