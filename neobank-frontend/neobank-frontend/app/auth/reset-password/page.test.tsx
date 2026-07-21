// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ResetPasswordPage from "./page";

const { resetPassword } = vi.hoisted(() => ({ resetPassword: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { resetPassword } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("email=carlos%40neobank.mx"),
}));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

beforeEach(() => {
  resetPassword.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fill(code: string, newPassword: string, confirm: string) {
  fireEvent.change(screen.getByPlaceholderText("123456"), { target: { value: code } });
  fireEvent.change(screen.getByPlaceholderText("Mínimo 8 caracteres"), { target: { value: newPassword } });
  fireEvent.change(screen.getByPlaceholderText("Repite tu contraseña"), { target: { value: confirm } });
}

describe("ResetPasswordPage", () => {
  it("mismatched passwords are rejected client-side without calling the API", async () => {
    render(<ResetPasswordPage />);
    fill("123456", "NewPass1!", "Different1!");
    fireEvent.click(screen.getByText("Restablecer contraseña"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Las contraseñas no coinciden"));
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it("on success, resets using the email from the query string and redirects to login", async () => {
    resetPassword.mockResolvedValue({ success: true });
    render(<ResetPasswordPage />);
    fill("123456", "NewPass1!", "NewPass1!");
    fireEvent.click(screen.getByText("Restablecer contraseña"));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/auth/login"));
    expect(resetPassword).toHaveBeenCalledWith("carlos@neobank.mx", "123456", "NewPass1!");
    expect(toastSuccess).toHaveBeenCalledWith("¡Contraseña restablecida!");
  });

  it("on server rejection (e.g. expired code), shows the error and does not navigate", async () => {
    resetPassword.mockRejectedValue({ response: { data: { message: "Code expired" } }, isAxiosError: true });
    render(<ResetPasswordPage />);
    fill("000000", "NewPass1!", "NewPass1!");
    fireEvent.click(screen.getByText("Restablecer contraseña"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Code expired"));
    expect(push).not.toHaveBeenCalled();
  });
});
