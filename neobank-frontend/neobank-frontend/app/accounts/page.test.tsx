// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import AccountsPage from "./page";
import type { Account } from "@/types";

const { getAllAccounts, freeze } = vi.hoisted(() => ({
  getAllAccounts: vi.fn(),
  freeze: vi.fn(),
}));
vi.mock("@/lib/api/accounts", () => ({ accountsApi: { getAll: getAllAccounts, freeze } }));

const { toastSuccess, toastError, toastDefault } = vi.hoisted(() => ({
  toastSuccess: vi.fn(), toastError: vi.fn(), toastDefault: vi.fn(),
}));
vi.mock("react-hot-toast", () => ({
  default: Object.assign(toastDefault, { success: toastSuccess, error: toastError }),
}));

const activeAccount: Account = {
  id: "acc-1", userId: "u1", accountNumber: "111111111111111111", accountType: "CHECKING",
  balance: 1000, availableBalance: 950, currency: "MXN", status: "ACTIVE",
  overdraftLimit: 500, interestRate: 2.5, lastTransactionAt: "2026-01-01T10:00:00Z",
};

const frozenAccount = {
  id: "acc-2", userId: "u1", accountNumber: "222222222222222222", accountType: "SAVINGS",
  balance: 5000, availableBalance: 5000, currency: "MXN", status: "FROZEN",
} as never;

beforeEach(() => {
  getAllAccounts.mockReset().mockResolvedValue({ data: { accounts: [activeAccount] } });
  freeze.mockReset().mockResolvedValue({ data: {} });
  toastSuccess.mockReset();
  toastError.mockReset();
  toastDefault.mockReset();
  Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("AccountsPage", () => {
  it("shows a spinner while loading, then renders the account cards", async () => {
    render(<AccountsPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("CHECKING")).toBeInTheDocument();
    expect(screen.getByText("$1,000.00")).toBeInTheDocument();
    expect(screen.getByText(/Disponible: \$950\.00/)).toBeInTheDocument();
  });

  it("renders an empty grid gracefully with no accounts", async () => {
    getAllAccounts.mockResolvedValue({ data: { accounts: [] } });
    render(<AccountsPage />);
    await waitFor(() => expect(document.querySelector(".animate-spin")).not.toBeInTheDocument());
    expect(screen.queryByText("CHECKING")).not.toBeInTheDocument();
  });

  it("degrades gracefully (stops loading, renders nothing) if the API call fails", async () => {
    getAllAccounts.mockRejectedValue(new Error("network"));
    render(<AccountsPage />);
    await waitFor(() => expect(document.querySelector(".animate-spin")).not.toBeInTheDocument());
    expect(screen.queryByText("CHECKING")).not.toBeInTheDocument();
  });

  it("copies the CLABE to the clipboard", async () => {
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    fireEvent.click(screen.getByText("Copiar CLABE"));
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("111111111111111111");
    expect(toastSuccess).toHaveBeenCalledWith("CLABE copiada");
  });

  it("shows a Congelar button for an ACTIVE account and Descongelar for a FROZEN one", async () => {
    getAllAccounts.mockResolvedValue({ data: { accounts: [activeAccount, frozenAccount] } });
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    expect(screen.getByText(/Congelar$/)).toBeInTheDocument();
    expect(screen.getByText(/Descongelar/)).toBeInTheDocument();
  });

  it("clicking Descongelar shows a support-contact toast (no unfreeze flow yet)", async () => {
    getAllAccounts.mockResolvedValue({ data: { accounts: [frozenAccount] } });
    render(<AccountsPage />);
    await screen.findByText("SAVINGS");
    fireEvent.click(screen.getByText(/Descongelar/));
    expect(toastDefault).toHaveBeenCalledWith("Contacta soporte para descongelar");
  });

  it("freezing an account: opens the modal, submits a reason, updates status and closes", async () => {
    render(<AccountsPage />);
    await screen.findByText("CHECKING");

    fireEvent.click(screen.getByText(/Congelar$/));
    expect(screen.getByRole("heading", { name: "Congelar cuenta" })).toBeInTheDocument();
    expect(screen.getByText(/••••1111/)).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Tarjeta perdida, actividad sospechosa…"), {
      target: { value: "Tarjeta perdida" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Congelar cuenta" }));

    await waitFor(() => expect(freeze).toHaveBeenCalledWith("acc-1", "Tarjeta perdida"));
    expect(await screen.findByText("FROZEN")).toBeInTheDocument();
    expect(toastSuccess).toHaveBeenCalledWith("Cuenta congelada");
  });

  it("Cancelar closes the freeze modal without calling the API", async () => {
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    fireEvent.click(screen.getByText(/Congelar$/));
    fireEvent.click(screen.getByText("Cancelar"));
    expect(freeze).not.toHaveBeenCalled();
  });

  it("a freeze failure shows the server error and keeps the account ACTIVE", async () => {
    freeze.mockRejectedValue({ response: { data: { message: "Server error" } }, isAxiosError: true });
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    fireEvent.click(screen.getByText(/Congelar$/));
    fireEvent.click(screen.getByRole("button", { name: "Congelar cuenta" }));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Server error"));
    expect(screen.getAllByText("ACTIVE").length).toBeGreaterThan(0);
  });

  it("shows a dash for missing lastTransactionAt/interestRate", async () => {
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    expect(screen.queryAllByText("—").length).toBe(0); // this account has both values set
  });

  it("shows dashes when lastTransactionAt/interestRate are absent", async () => {
    const bare = { ...activeAccount, lastTransactionAt: undefined, interestRate: undefined, overdraftLimit: undefined };
    getAllAccounts.mockResolvedValue({ data: { accounts: [bare] } });
    render(<AccountsPage />);
    await screen.findByText("CHECKING");
    expect(screen.getAllByText("—").length).toBe(2);
  });
});
