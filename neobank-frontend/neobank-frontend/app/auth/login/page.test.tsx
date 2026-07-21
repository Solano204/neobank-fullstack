// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import LoginPage from "./page";
import { useAuthStore } from "@/lib/store/authStore";

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { login } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

beforeEach(() => {
  login.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fillAndSubmit() {
  fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "carlos@neobank.mx" } });
  fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "P@ssw0rd1" } });
  fireEvent.click(screen.getByText("Iniciar sesión", { selector: "button" }));
}

describe("LoginPage", () => {
  it("renders links to signup and forgot-password", () => {
    render(<LoginPage />);
    expect(screen.getByText("Regístrate gratis")).toHaveAttribute("href", "/auth/signup");
    expect(screen.getByText("¿Olvidaste tu contraseña?")).toHaveAttribute("href", "/auth/forgot-password");
  });

  it("on success, stores tokens/user, shows a toast, and navigates to /dashboard", async () => {
    login.mockResolvedValue({
      data: { accessToken: "access-1", refreshToken: "refresh-1", user: { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos", kycStatus: "PENDING" } },
    });
    render(<LoginPage />);

    fillAndSubmit();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/dashboard"));
    expect(login).toHaveBeenCalledWith({ email: "carlos@neobank.mx", password: "P@ssw0rd1" });
    expect(toastSuccess).toHaveBeenCalledWith("¡Bienvenido de vuelta!");
    expect(useAuthStore.getState().isLoggedIn).toBe(true);
  });

  it("on failure, shows an error toast and does not navigate", async () => {
    login.mockRejectedValue({ response: { data: { message: "Invalid email or password" } }, isAxiosError: true });
    render(<LoginPage />);

    fillAndSubmit();

    await waitFor(() => expect(toastError).toHaveBeenCalled());
    expect(push).not.toHaveBeenCalled();
    expect(useAuthStore.getState().isLoggedIn).toBe(false);
  });

  it("disables the submit button while the request is in flight", async () => {
    let resolveLogin!: (v: unknown) => void;
    login.mockReturnValue(new Promise((res) => { resolveLogin = res; }));
    render(<LoginPage />);

    fillAndSubmit();
    expect(screen.getByText("Iniciar sesión", { selector: "button" })).toBeDisabled();

    resolveLogin({ data: { accessToken: "a", refreshToken: "r", user: { id: "u1", email: "e", fullName: "n", kycStatus: "PENDING" } } });
    await waitFor(() => expect(screen.getByText("Iniciar sesión", { selector: "button" })).not.toBeDisabled());
  });
});
