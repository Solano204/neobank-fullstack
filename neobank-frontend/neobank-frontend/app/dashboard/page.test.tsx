// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import DashboardPage from "./page";
import { useAuthStore } from "@/lib/store/authStore";

const { getAllAccounts, getHistory, getAllNotifications } = vi.hoisted(() => ({
  getAllAccounts: vi.fn(),
  getHistory: vi.fn(),
  getAllNotifications: vi.fn(),
}));
vi.mock("@/lib/api/accounts", () => ({ accountsApi: { getAll: getAllAccounts } }));
vi.mock("@/lib/api/transactions", () => ({ transactionsApi: { getHistory } }));
vi.mock("@/lib/api/notifications", () => ({ notificationsApi: { getAll: getAllNotifications } }));

const account = {
  id: "acc-1", userId: "u1", accountNumber: "111111111111111111", accountType: "CHECKING",
  balance: 1000, availableBalance: 1000, currency: "MXN", status: "ACTIVE",
} as never;

const depositTx = {
  id: "tx-1", fromAccount: "222", toAccount: "111111111111111111", amount: 100, currency: "MXN",
  status: "COMPLETED", type: "DEPOSIT", description: "Payroll", createdAt: "2026-01-01T10:00:00Z",
} as never;

beforeEach(() => {
  getAllAccounts.mockReset().mockResolvedValue({ data: { accounts: [account] } });
  getHistory.mockReset().mockResolvedValue({ data: { transactions: [depositTx], total: 1, page: 1, limit: 5 } });
  getAllNotifications.mockReset().mockResolvedValue({ data: { notifications: [] } });
  useAuthStore.setState({ user: null, isLoggedIn: false, isLoading: false });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("DashboardPage", () => {
  it("shows a spinner while loading, then the balance card", async () => {
    render(<DashboardPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("Balance total")).toBeInTheDocument();
  });

  it("shows the total balance summed across accounts", async () => {
    render(<DashboardPage />);
    const heading = await screen.findByRole("heading", { level: 2 });
    expect(heading).toHaveTextContent("$1,000.00");
  });

  it("toggling the eye icon hides/shows the balance", async () => {
    render(<DashboardPage />);
    const heading = await screen.findByRole("heading", { level: 2 });
    expect(heading).toHaveTextContent("$1,000.00");
    const toggle = document.querySelector(".balance-card button")!;

    fireEvent.click(toggle);
    expect(heading).toHaveTextContent("••••••");

    fireEvent.click(toggle);
    expect(heading).toHaveTextContent("$1,000.00");
  });

  it("shows recent transactions with sign/color based on type", async () => {
    render(<DashboardPage />);
    expect(await screen.findByText("Payroll")).toBeInTheDocument();
    expect(screen.getByText("+$100.00")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED")).toBeInTheDocument();
  });

  it('shows "Sin movimientos aún" when there are no transactions', async () => {
    getHistory.mockResolvedValue({ data: { transactions: [], total: 0, page: 1, limit: 5 } });
    render(<DashboardPage />);
    expect(await screen.findByText("Sin movimientos aún")).toBeInTheDocument();
  });

  it("shows an unread-notification badge count", async () => {
    getAllNotifications.mockResolvedValue({
      data: {
        notifications: [
          { id: "n1", type: "TRANSACTION", title: "Payment received", message: "m", read: false, createdAt: "" },
          { id: "n2", type: "SYSTEM", title: "Welcome", message: "m", read: true, createdAt: "" },
        ],
      },
    });
    render(<DashboardPage />);
    expect(await screen.findByText("1")).toBeInTheDocument(); // unread count badge
    expect(screen.getByText("Payment received")).toBeInTheDocument();
  });

  it('shows "Sin notificaciones" when there are none', async () => {
    render(<DashboardPage />);
    expect(await screen.findByText("Sin notificaciones")).toBeInTheDocument();
  });

  it("shows the KYC verification CTA only when the user's KYC is PENDING", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", fullName: "A B", kycStatus: "PENDING" } as never });
    render(<DashboardPage />);
    expect(await screen.findByText("Verifica tu identidad")).toBeInTheDocument();
  });

  it("does not show the KYC CTA once the user is already verified", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", fullName: "A B", kycStatus: "APPROVED" } as never });
    render(<DashboardPage />);
    await screen.findByText("Balance total");
    expect(screen.queryByText("Verifica tu identidad")).not.toBeInTheDocument();
  });

  it("degrades gracefully (still renders) if the accounts call fails", async () => {
    getAllAccounts.mockRejectedValue(new Error("network"));
    render(<DashboardPage />);
    expect(await screen.findByText("Balance total")).toBeInTheDocument();
    expect(screen.getByText("$0.00")).toBeInTheDocument();
  });

  it("links the quick actions to their respective pages", async () => {
    render(<DashboardPage />);
    await screen.findByText("Balance total");
    expect(screen.getByText("Transferir").closest("a")).toHaveAttribute("href", "/transfer");
    expect(screen.getByText("Cuentas").closest("a")).toHaveAttribute("href", "/accounts");
  });
});
