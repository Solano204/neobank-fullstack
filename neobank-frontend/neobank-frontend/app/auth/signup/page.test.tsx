// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import SignupPage from "./page";

const { signup } = vi.hoisted(() => ({ signup: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { signup } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

beforeEach(() => {
  signup.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fillValidForm() {
  fireEvent.change(screen.getByPlaceholderText("Carlos Mendoza"), { target: { value: "Carlos Perez" } });
  fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "carlos@neobank.mx" } });
  fireEvent.change(screen.getByPlaceholderText("Mínimo 8 caracteres"), { target: { value: "P@ssw0rd1" } });
  fireEvent.change(screen.getByPlaceholderText("+52 999 123 4567"), { target: { value: "+525512345678" } });
}

describe("SignupPage", () => {
  // fullName/email are both HTML `required` AND custom-validated on the
  // exact same "is it empty" condition, so leaving them empty never reaches
  // the custom validate() function at all - the browser's native constraint
  // validation blocks the submit event first (confirmed: jsdom enforces
  // this). Only password (too-short, not empty) and phone (wrong shape, not
  // empty) can actually exercise validate()'s messages via a real submit.
  it("a too-short password is rejected client-side without calling the API", async () => {
    render(<SignupPage />);
    fireEvent.change(screen.getByPlaceholderText("Carlos Mendoza"), { target: { value: "Carlos" } });
    fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "c@b.com" } });
    fireEvent.change(screen.getByPlaceholderText("Mínimo 8 caracteres"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("+52 999 123 4567"), { target: { value: "+525512345678" } });
    fireEvent.click(screen.getByText("Crear cuenta gratis"));

    expect(await screen.findByText("Mínimo 8 caracteres", { selector: "p" })).toBeInTheDocument();
    expect(signup).not.toHaveBeenCalled();
  });

  it("rejects a malformed phone number specifically", async () => {
    render(<SignupPage />);
    fireEvent.change(screen.getByPlaceholderText("Carlos Mendoza"), { target: { value: "Carlos" } });
    fireEvent.change(screen.getByPlaceholderText("carlos@ejemplo.com"), { target: { value: "c@b.com" } });
    fireEvent.change(screen.getByPlaceholderText("Mínimo 8 caracteres"), { target: { value: "P@ssw0rd1" } });
    fireEvent.change(screen.getByPlaceholderText("+52 999 123 4567"), { target: { value: "abc" } });
    fireEvent.click(screen.getByText("Crear cuenta gratis"));

    expect(await screen.findByText("Teléfono inválido")).toBeInTheDocument();
    expect(signup).not.toHaveBeenCalled();
  });

  it("on a valid form, signs up and redirects to verify-email with the email in the query string", async () => {
    signup.mockResolvedValue({ data: { message: "ok" } });
    render(<SignupPage />);
    fillValidForm();
    fireEvent.click(screen.getByText("Crear cuenta gratis"));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/auth/verify-email?email=carlos%40neobank.mx"));
    expect(toastSuccess).toHaveBeenCalledWith("¡Cuenta creada! Revisa tu correo.");
  });

  it("on a duplicate-email failure, shows the server's error toast and does not navigate", async () => {
    signup.mockRejectedValue({ response: { data: { message: "Email already registered" } }, isAxiosError: true });
    render(<SignupPage />);
    fillValidForm();
    fireEvent.click(screen.getByText("Crear cuenta gratis"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Email already registered"));
    expect(push).not.toHaveBeenCalled();
  });

  it("links back to the login page", () => {
    render(<SignupPage />);
    expect(screen.getByText("Inicia sesión")).toHaveAttribute("href", "/auth/login");
  });
});
