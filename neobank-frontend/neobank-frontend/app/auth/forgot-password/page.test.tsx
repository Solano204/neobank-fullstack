// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ForgotPasswordPage from "./page";

const { forgotPassword } = vi.hoisted(() => ({ forgotPassword: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { forgotPassword } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

beforeEach(() => {
  forgotPassword.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ForgotPasswordPage", () => {
  it("on success, sends the code and redirects to reset-password with the email preserved", async () => {
    forgotPassword.mockResolvedValue({ success: true });
    render(<ForgotPasswordPage />);

    fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "carlos@neobank.mx" } });
    fireEvent.click(screen.getByText("Enviar código"));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/auth/reset-password?email=carlos%40neobank.mx"));
    expect(forgotPassword).toHaveBeenCalledWith("carlos@neobank.mx");
    expect(toastSuccess).toHaveBeenCalledWith("¡Código enviado a tu correo!");
  });

  it("on failure, shows an error toast and does not navigate", async () => {
    forgotPassword.mockRejectedValue({ response: { data: { message: "Email not found" } }, isAxiosError: true });
    render(<ForgotPasswordPage />);

    fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "nope@neobank.mx" } });
    fireEvent.click(screen.getByText("Enviar código"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Email not found"));
    expect(push).not.toHaveBeenCalled();
  });

  it("links back to login", () => {
    render(<ForgotPasswordPage />);
    expect(screen.getByText(/Volver al inicio de sesión/)).toHaveAttribute("href", "/auth/login");
  });
});
