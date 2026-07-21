// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import VerifyEmailPage from "./page";
import { useAuthStore } from "@/lib/store/authStore";

const { verifyEmail, resendCode } = vi.hoisted(() => ({ verifyEmail: vi.fn(), resendCode: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { verifyEmail, resendCode } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("email=carlos%40neobank.mx"),
}));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

beforeEach(() => {
  verifyEmail.mockReset();
  resendCode.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("VerifyEmailPage", () => {
  it("shows the email pulled from the query string", () => {
    render(<VerifyEmailPage />);
    expect(screen.getByText("carlos@neobank.mx")).toBeInTheDocument();
  });

  it("the code field strips non-digit characters as the user types", () => {
    render(<VerifyEmailPage />);
    const input = screen.getByPlaceholderText("123456");
    fireEvent.change(input, { target: { value: "12a3b4" } });
    expect(input).toHaveValue("1234");
  });

  it("on success, stores tokens/user and redirects to /dashboard", async () => {
    verifyEmail.mockResolvedValue({
      data: { accessToken: "a1", refreshToken: "r1", user: { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos", kycStatus: "PENDING" } },
    });
    render(<VerifyEmailPage />);
    fireEvent.change(screen.getByPlaceholderText("123456"), { target: { value: "123456" } });
    fireEvent.click(screen.getByText("Verificar correo"));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/dashboard"));
    expect(verifyEmail).toHaveBeenCalledWith("carlos@neobank.mx", "123456");
    expect(useAuthStore.getState().isLoggedIn).toBe(true);
  });

  it("on an invalid code, shows an error toast and does not navigate", async () => {
    verifyEmail.mockRejectedValue({ response: { data: { message: "Invalid code" } }, isAxiosError: true });
    render(<VerifyEmailPage />);
    fireEvent.change(screen.getByPlaceholderText("123456"), { target: { value: "000000" } });
    fireEvent.click(screen.getByText("Verificar correo"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Invalid code"));
    expect(push).not.toHaveBeenCalled();
  });

  it("resend starts a 60s cooldown that disables the button and counts down", async () => {
    vi.useFakeTimers();
    resendCode.mockResolvedValue({ success: true });
    render(<VerifyEmailPage />);

    await act(async () => {
      fireEvent.click(screen.getByText("Reenviar código"));
      await Promise.resolve(); // let the resendCode promise settle
    });

    expect(screen.getByText("Reenviar en 60s")).toBeDisabled();

    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText("Reenviar en 55s")).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(55000);
    });
    expect(screen.getByText("Reenviar código")).not.toBeDisabled();
  });

  it("resend failure shows an error toast", async () => {
    resendCode.mockRejectedValue({ response: { data: { message: "Too many requests" } }, isAxiosError: true });
    render(<VerifyEmailPage />);
    fireEvent.click(screen.getByText("Reenviar código"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Too many requests"));
  });
});
