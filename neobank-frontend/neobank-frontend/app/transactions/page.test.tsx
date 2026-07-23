// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import TransactionsPage from "./page";

const { getAllAccounts, getHistory } = vi.hoisted(() => ({
  getAllAccounts: vi.fn(),
  getHistory: vi.fn(),
}));
vi.mock("@/lib/api/accounts", () => ({ accountsApi: { getAll: getAllAccounts } }));
vi.mock("@/lib/api/transactions", () => ({ transactionsApi: { getHistory } }));

const account = { id: "acc-1", accountNumber: "111111111111111111", accountType: "CHECKING", balance: 1000, availableBalance: 1000, currency: "MXN", status: "ACTIVE", userId: "u1" } as never;

const depositTx = {
  id: "tx-1", fromAccount: "222", toAccount: "111111111111111111", amount: 100, currency: "MXN",
  status: "COMPLETED", type: "DEPOSIT", description: "Payroll", createdAt: "2026-01-01T10:00:00Z",
} as never;
const withdrawalTx = {
  id: "tx-2", fromAccount: "111111111111111111", toAccount: "333333333333333333", amount: 50, currency: "MXN",
  status: "PENDING", type: "WITHDRAWAL", description: "Rent", createdAt: "2026-01-02T10:00:00Z",
} as never;

beforeEach(() => {
  getAllAccounts.mockReset().mockResolvedValue({ data: { accounts: [account] } });
  getHistory.mockReset().mockResolvedValue({ data: { transactions: [depositTx, withdrawalTx], total: 2, page: 1, limit: 20 } });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("TransactionsPage", () => {
  it("loads the first account, then fetches and renders its transaction history", async () => {
    render(<TransactionsPage />);
    expect(await screen.findByText("Payroll")).toBeInTheDocument();
    expect(screen.getByText("Rent")).toBeInTheDocument();
    expect(getHistory).toHaveBeenCalledWith("111111111111111111", 1, 20);
  });

  it('shows "Sin movimientos" when the account has none', async () => {
    getHistory.mockResolvedValue({ data: { transactions: [], total: 0, page: 1, limit: 20 } });
    render(<TransactionsPage />);
    expect(await screen.findByText("Sin movimientos")).toBeInTheDocument();
  });

  it("never fetches history if the user has no accounts", async () => {
    getAllAccounts.mockResolvedValue({ data: { accounts: [] } });
    render(<TransactionsPage />);
    await waitFor(() => expect(getAllAccounts).toHaveBeenCalled());
    expect(getHistory).not.toHaveBeenCalled();
  });

  it("filters by search text matching the description", async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    fireEvent.change(screen.getByPlaceholderText("Buscar movimientos…"), { target: { value: "rent" } });
    expect(screen.queryByText("Payroll")).not.toBeInTheDocument();
    expect(screen.getByText("Rent")).toBeInTheDocument();
  });

  it("filters by status via the quick-filter buttons", async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    fireEvent.click(screen.getByText("Pendientes"));
    expect(screen.queryByText("Payroll")).not.toBeInTheDocument();
    expect(screen.getByText("Rent")).toBeInTheDocument();
  });

  it('search with no matches shows the "Sin movimientos" empty state', async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    fireEvent.change(screen.getByPlaceholderText("Buscar movimientos…"), { target: { value: "nonexistent" } });
    expect(screen.getByText("Sin movimientos")).toBeInTheDocument();
  });

  it("shows the total count and current page", async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    expect(screen.getByText("Total: 2 movimientos")).toBeInTheDocument();
    expect(screen.getByText("Pág. 1")).toBeInTheDocument();
  });

  function paginationButtons() {
    const container = screen.getByText(/^Total:/).closest("div")!;
    return container.querySelectorAll("button");
  }

  it("the previous-page button is disabled on the first page", async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    expect(paginationButtons()[0]).toBeDisabled();
  });

  it("clicking next page requests page 2 from the API", async () => {
    getHistory.mockResolvedValue({ data: { transactions: [depositTx, withdrawalTx], total: 25, page: 1, limit: 20 } });
    render(<TransactionsPage />);
    await screen.findByText("Payroll");

    fireEvent.click(paginationButtons()[1]); // next-page button (chevron-right)

    await waitFor(() => expect(getHistory).toHaveBeenCalledWith("111111111111111111", 2, 20));
  });

  it("the next-page button is disabled once all results are on the current page", async () => {
    getHistory.mockResolvedValue({ data: { transactions: [depositTx], total: 1, page: 1, limit: 20 } });
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    expect(paginationButtons()[1]).toBeDisabled();
  });

  it("shows the deposit destination account's last 4 digits", async () => {
    render(<TransactionsPage />);
    await screen.findByText("Payroll");
    expect(screen.getByText("→ ••••1111")).toBeInTheDocument();
  });
});
