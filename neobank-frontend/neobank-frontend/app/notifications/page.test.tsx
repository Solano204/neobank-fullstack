// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import NotificationsPage from "./page";

const { getAll, markRead, markAllRead } = vi.hoisted(() => ({
  getAll: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(),
}));
vi.mock("@/lib/api/notifications", () => ({ notificationsApi: { getAll, markRead, markAllRead } }));

const { toastSuccess } = vi.hoisted(() => ({ toastSuccess: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess } }));

const unreadTx = { id: "n1", type: "TRANSACTION", title: "Pago recibido", message: "Recibiste $100.00", read: false, createdAt: "2026-01-01T10:00:00Z" };
const readSecurity = { id: "n2", type: "SECURITY", title: "Nuevo inicio de sesión", message: "Detectamos un inicio de sesión", read: true, createdAt: "2026-01-02T10:00:00Z" };

beforeEach(() => {
  getAll.mockReset().mockResolvedValue({ data: { notifications: [unreadTx, readSecurity] } });
  markRead.mockReset().mockResolvedValue({ data: {} });
  markAllRead.mockReset().mockResolvedValue({ data: {} });
  toastSuccess.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("NotificationsPage", () => {
  it("shows a spinner while loading, then the notification list", async () => {
    render(<NotificationsPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("Pago recibido")).toBeInTheDocument();
    expect(screen.getByText("Nuevo inicio de sesión")).toBeInTheDocument();
  });

  it('shows "Sin notificaciones" when the list is empty', async () => {
    getAll.mockResolvedValue({ data: { notifications: [] } });
    render(<NotificationsPage />);
    expect(await screen.findByText("Sin notificaciones")).toBeInTheDocument();
  });

  it("shows the unread count, and hides the mark-all button when everything is read", async () => {
    render(<NotificationsPage />);
    expect(await screen.findByText("1 sin leer")).toBeInTheDocument();
    expect(screen.getByText("Marcar todas como leídas")).toBeInTheDocument();
  });

  it('shows "Todo al día" and hides the mark-all button when nothing is unread', async () => {
    getAll.mockResolvedValue({ data: { notifications: [readSecurity] } });
    render(<NotificationsPage />);
    expect(await screen.findByText("Todo al día")).toBeInTheDocument();
    expect(screen.queryByText("Marcar todas como leídas")).not.toBeInTheDocument();
  });

  it("clicking an unread notification marks it read via the API", async () => {
    render(<NotificationsPage />);
    await screen.findByText("Pago recibido");
    fireEvent.click(screen.getByText("Pago recibido"));

    await waitFor(() => expect(markRead).toHaveBeenCalledWith("n1"));
    expect(await screen.findByText("Todo al día")).toBeInTheDocument();
  });

  it("clicking an already-read notification does not call the API", async () => {
    render(<NotificationsPage />);
    await screen.findByText("Nuevo inicio de sesión");
    fireEvent.click(screen.getByText("Nuevo inicio de sesión"));
    expect(markRead).not.toHaveBeenCalled();
  });

  it('"Marcar todas como leídas" marks every notification read and shows a toast', async () => {
    render(<NotificationsPage />);
    await screen.findByText("1 sin leer");
    fireEvent.click(screen.getByText("Marcar todas como leídas"));

    await waitFor(() => expect(markAllRead).toHaveBeenCalled());
    expect(await screen.findByText("Todo al día")).toBeInTheDocument();
    expect(toastSuccess).toHaveBeenCalledWith("Todas marcadas como leídas");
  });

  it("falls back to a generic bell icon for an unrecognized notification type", async () => {
    getAll.mockResolvedValue({ data: { notifications: [{ ...unreadTx, type: "UNKNOWN_TYPE" }] } });
    render(<NotificationsPage />);
    await screen.findByText("Pago recibido");
    expect(document.querySelector(".lucide-bell")).toBeInTheDocument();
  });

  it("degrades gracefully (stops loading) if the API call fails", async () => {
    getAll.mockRejectedValue(new Error("network"));
    render(<NotificationsPage />);
    await waitFor(() => expect(document.querySelector(".animate-spin")).not.toBeInTheDocument());
    expect(screen.getByText("Sin notificaciones")).toBeInTheDocument();
  });
});
