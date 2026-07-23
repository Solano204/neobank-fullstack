// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import TransferPage from "./page";
import { useAuthStore } from "@/lib/store/authStore";

const { getAllAccounts, getAllContacts, validateRecipient, transfer } = vi.hoisted(() => ({
  getAllAccounts: vi.fn(),
  getAllContacts: vi.fn(),
  validateRecipient: vi.fn(),
  transfer: vi.fn(),
}));
vi.mock("@/lib/api/accounts", () => ({ accountsApi: { getAll: getAllAccounts } }));
vi.mock("@/lib/api/contacts", () => ({ contactsApi: { getAll: getAllContacts } }));
vi.mock("@/lib/api/transactions", () => ({ transactionsApi: { validateRecipient, transfer } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

const account = {
  id: "acc-1", userId: "u1", accountNumber: "111111111111111111", accountType: "CHECKING",
  balance: 1000, availableBalance: 1000, currency: "MXN", status: "ACTIVE",
} as never;

const contact = { id: "c1", name: "Ana Lopez", accountNumber: "222222222222222222", nickname: "Ana", favorite: false, createdAt: "" } as never;

beforeEach(() => {
  getAllAccounts.mockReset().mockResolvedValue({ data: { accounts: [account] } });
  getAllContacts.mockReset().mockResolvedValue({ data: { contacts: [contact] } });
  validateRecipient.mockReset();
  transfer.mockReset();
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

afterEach(() => {
  vi.restoreAllMocks();
});

async function waitForAccountsLoaded() {
  expect(await screen.findByText("CHECKING")).toBeInTheDocument();
}

describe("TransferPage — form step", () => {
  it("preselects the first account as the source", async () => {
    render(<TransferPage />);
    await waitForAccountsLoaded();
    const radio = document.querySelector('input[type="radio"]') as HTMLInputElement;
    expect(radio.checked).toBe(true);
  });

  it("shows frequent contacts and selecting one fills the destination + validates it", async () => {
    validateRecipient.mockResolvedValue({ data: { valid: true, name: "Ana Lopez" } });
    render(<TransferPage />);
    await waitForAccountsLoaded();

    fireEvent.click(screen.getByText("Ana"));

    expect(await screen.findByText("Ana Lopez")).toBeInTheDocument();
    expect(validateRecipient).toHaveBeenCalledWith("222222222222222222");
  });

  it("typing a full 18-digit CLABE auto-validates and shows the recipient's name", async () => {
    validateRecipient.mockResolvedValue({ data: { valid: true, name: "Someone Else" } });
    render(<TransferPage />);
    await waitForAccountsLoaded();

    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "333333333333333333" } });

    await waitFor(() => expect(validateRecipient).toHaveBeenCalledWith("333333333333333333"));
    expect(await screen.findByText("Someone Else")).toBeInTheDocument();
  });

  it("the CLABE input strips non-digits and caps at 18 characters", async () => {
    render(<TransferPage />);
    await waitForAccountsLoaded();
    const input = screen.getByPlaceholderText("000000000000000000");
    fireEvent.change(input, { target: { value: "abc333333333333333333999" } });
    expect(input).toHaveValue("333333333333333333");
  });

  it("an unknown recipient account shows an error and no green check", async () => {
    validateRecipient.mockResolvedValue({ data: { valid: false } });
    render(<TransferPage />);
    await waitForAccountsLoaded();

    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "999999999999999999" } });

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Cuenta no encontrada"));
  });

  it("Continue is disabled until an amount is entered", async () => {
    render(<TransferPage />);
    await waitForAccountsLoaded();
    expect(screen.getByText(/Continuar/)).toBeDisabled();

    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "222222222222222222" } });
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "100" } });

    expect(screen.getByText(/Continuar/)).not.toBeDisabled();
  });

  it("Continue stays disabled for a zero or negative amount", async () => {
    render(<TransferPage />);
    await waitForAccountsLoaded();
    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "222222222222222222" } });
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "0" } });
    expect(screen.getByText(/Continuar/)).toBeDisabled();
  });
});

describe("TransferPage — confirm & success steps", () => {
  async function goToConfirm() {
    render(<TransferPage />);
    await waitForAccountsLoaded();
    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "222222222222222222" } });
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "150" } });
    fireEvent.change(screen.getByPlaceholderText("Renta, nómina, regalo…"), { target: { value: "Rent" } });
    fireEvent.click(screen.getByText(/Continuar/));
  }

  it("shows a summary with the computed post-transfer balance", async () => {
    await goToConfirm();
    expect(screen.getByText("Confirmar transferencia")).toBeInTheDocument();
    expect(screen.getByText("$150.00")).toBeInTheDocument(); // Monto
    expect(screen.getByText("$850.00")).toBeInTheDocument(); // 1000 - 150
  });

  it("Editar goes back to the form step without submitting", async () => {
    await goToConfirm();
    fireEvent.click(screen.getByText("Editar"));
    expect(screen.getByText("Cuenta origen")).toBeInTheDocument();
    expect(transfer).not.toHaveBeenCalled();
  });

  it("confirming sends the transfer with parsed amount and fixed MXN currency", async () => {
    transfer.mockResolvedValue({ data: { transactionId: "tx-1", newBalance: 850 } });
    await goToConfirm();

    fireEvent.click(screen.getByText("Confirmar y enviar"));

    await waitFor(() => expect(transfer).toHaveBeenCalledWith({
      from_account: "111111111111111111", to_account: "222222222222222222",
      amount: 150, currency: "MXN", description: "Rent",
    }));
    expect(await screen.findByText("¡Transferencia exitosa!")).toBeInTheDocument();
    expect(screen.getByText(/tx-1/)).toBeInTheDocument();
  });

  it("a failed transfer shows the error and returns to the form step", async () => {
    transfer.mockRejectedValue({ response: { data: { message: "Insufficient funds" } }, isAxiosError: true });
    await goToConfirm();

    fireEvent.click(screen.getByText("Confirmar y enviar"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Insufficient funds"));
    expect(screen.getByText("Cuenta origen")).toBeInTheDocument(); // back to form
  });

  it('"Nueva transferencia" resets the form and returns to step "form"', async () => {
    transfer.mockResolvedValue({ data: { transactionId: "tx-1", newBalance: 850 } });
    await goToConfirm();
    fireEvent.click(screen.getByText("Confirmar y enviar"));
    await screen.findByText("¡Transferencia exitosa!");

    fireEvent.click(screen.getByText("Nueva transferencia"));

    expect(screen.getByText("Cuenta origen")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("000000000000000000")).toHaveValue("");
  });

  it('"Ir al dashboard" navigates to /dashboard', async () => {
    transfer.mockResolvedValue({ data: { transactionId: "tx-1", newBalance: 850 } });
    await goToConfirm();
    fireEvent.click(screen.getByText("Confirmar y enviar"));
    await screen.findByText("¡Transferencia exitosa!");

    fireEvent.click(screen.getByText("Ir al dashboard"));
    expect(push).toHaveBeenCalledWith("/dashboard");
  });
});
