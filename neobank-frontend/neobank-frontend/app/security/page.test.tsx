// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import SecurityPage from "./page";

const { getSessions, getAlerts, deleteSession, deleteAllSessions, enableMfa, verifyMfa, confirmAlert, reportAlert } = vi.hoisted(() => ({
  getSessions: vi.fn(), getAlerts: vi.fn(), deleteSession: vi.fn(), deleteAllSessions: vi.fn(),
  enableMfa: vi.fn(), verifyMfa: vi.fn(), confirmAlert: vi.fn(), reportAlert: vi.fn(),
}));
vi.mock("@/lib/api/security", () => ({
  securityApi: { getSessions, getAlerts, deleteSession, deleteAllSessions, enableMfa, verifyMfa, confirmAlert, reportAlert },
}));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

const session = { id: "s1", device: "iPhone 15", location: "CDMX", ipAddress: "1.2.3.4", lastActive: "2026-01-01T10:00:00Z" };
const alert = { id: "a1", severity: "HIGH", message: "Cargo inusual detectado", createdAt: "2026-01-01T10:00:00Z" };

beforeEach(() => {
  getSessions.mockReset().mockResolvedValue({ data: { sessions: [session] } });
  // Matches production reality today: SecurityController has real sessions,
  // but the fraud-alert feed and MFA are documented not-implemented stubs
  // (see lib/api/security.ts) that always reject. Individual tests override
  // this to also exercise the success paths for when a backend lands.
  getAlerts.mockReset().mockRejectedValue(new Error("getAlerts has no backend yet"));
  deleteSession.mockReset().mockResolvedValue({ data: {} });
  deleteAllSessions.mockReset().mockResolvedValue({ data: {} });
  enableMfa.mockReset().mockRejectedValue(new Error("enableMfa has no backend yet"));
  verifyMfa.mockReset().mockRejectedValue(new Error("verifyMfa has no backend yet"));
  confirmAlert.mockReset().mockResolvedValue({ data: {} });
  reportAlert.mockReset().mockResolvedValue({ data: {} });
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SecurityPage", () => {
  it("shows a spinner while loading, then the session list", async () => {
    render(<SecurityPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("iPhone 15")).toBeInTheDocument();
    expect(screen.getByText("CDMX · 1.2.3.4")).toBeInTheDocument();
  });

  it('shows "Sin sesiones activas" when there are none', async () => {
    getSessions.mockResolvedValue({ data: { sessions: [] } });
    render(<SecurityPage />);
    expect(await screen.findByText("Sin sesiones activas")).toBeInTheDocument();
  });

  it("falls back to a generic device label when device is missing", async () => {
    getSessions.mockResolvedValue({ data: { sessions: [{ ...session, device: "" }] } });
    render(<SecurityPage />);
    expect(await screen.findByText("Dispositivo desconocido")).toBeInTheDocument();
  });

  it("does not render the alerts section when getAlerts fails (current production reality: not implemented)", async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    expect(screen.queryByText("Alertas de seguridad")).not.toBeInTheDocument();
  });

  it("renders fraud alerts when the feed succeeds", async () => {
    getAlerts.mockResolvedValue({ data: { alerts: [alert] } });
    render(<SecurityPage />);
    expect(await screen.findByText("Alertas de seguridad")).toBeInTheDocument();
    expect(screen.getByText("Cargo inusual detectado")).toBeInTheDocument();
    expect(screen.getByText("HIGH")).toBeInTheDocument();
  });

  it('"Fui yo" confirms the alert and removes it from the list', async () => {
    getAlerts.mockResolvedValue({ data: { alerts: [alert] } });
    render(<SecurityPage />);
    await screen.findByText("Cargo inusual detectado");

    fireEvent.click(screen.getByText("Fui yo"));

    await waitFor(() => expect(confirmAlert).toHaveBeenCalledWith("a1"));
    await waitFor(() => expect(screen.queryByText("Cargo inusual detectado")).not.toBeInTheDocument());
    expect(toastSuccess).toHaveBeenCalledWith("Confirmado como legítimo");
  });

  it('"Reportar" opens the report modal, and submitting reports + removes the alert', async () => {
    getAlerts.mockResolvedValue({ data: { alerts: [alert] } });
    render(<SecurityPage />);
    await screen.findByText("Cargo inusual detectado");

    fireEvent.click(screen.getByText("Reportar"));
    expect(screen.getByText("Reportar transacción")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("No reconozco este cargo…"), { target: { value: "No fui yo" } });
    fireEvent.click(screen.getByText("Reportar y congelar cuenta"));

    await waitFor(() => expect(reportAlert).toHaveBeenCalledWith("a1", "No fui yo"));
    expect(toastSuccess).toHaveBeenCalledWith("Transacción reportada. Tu cuenta está protegida.");
    await waitFor(() => expect(screen.queryByText("Cargo inusual detectado")).not.toBeInTheDocument());
  });

  it("terminating a single session calls the API and removes it from the list", async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(document.querySelector('button svg.lucide-x')!.closest("button")!);

    await waitFor(() => expect(deleteSession).toHaveBeenCalledWith("s1"));
    expect(toastSuccess).toHaveBeenCalledWith("Sesión cerrada");
    await waitFor(() => expect(screen.queryByText("iPhone 15")).not.toBeInTheDocument());
  });

  it("a failed session termination shows the error and keeps the session listed", async () => {
    deleteSession.mockRejectedValue({ response: { data: { message: "Server error" } }, isAxiosError: true });
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(document.querySelector('button svg.lucide-x')!.closest("button")!);

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Server error"));
    expect(screen.getByText("iPhone 15")).toBeInTheDocument();
  });

  it('"Cerrar todas" clears every session', async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(screen.getByText("Cerrar todas"));

    await waitFor(() => expect(deleteAllSessions).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByText("iPhone 15")).not.toBeInTheDocument());
    expect(toastSuccess).toHaveBeenCalledWith("Todas las sesiones cerradas");
  });

  it("Activar MFA opens the modal and requests a code (which fails today - no backend)", async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(screen.getByText("Activar MFA"));

    expect(screen.getByText("Hemos enviado un código de verificación a tu teléfono registrado.")).toBeInTheDocument();
    await waitFor(() => expect(enableMfa).toHaveBeenCalledWith("SMS"));
    await waitFor(() => expect(toastError).toHaveBeenCalled()); // not-implemented rejection surfaces as an error toast
  });

  it("the MFA verify button stays disabled until 6 digits are entered", async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(screen.getByText("Activar MFA"));

    const codeInput = screen.getByPlaceholderText("123456");
    const verifyButtons = screen.getAllByText("Activar MFA");
    const verifyButton = verifyButtons[verifyButtons.length - 1];
    expect(verifyButton).toBeDisabled();

    fireEvent.change(codeInput, { target: { value: "123456" } });
    expect(verifyButton).not.toBeDisabled();
  });

  it("submitting a valid MFA code calls verifyMfa and closes the modal on success", async () => {
    verifyMfa.mockResolvedValue({ data: {} });
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(screen.getByText("Activar MFA"));

    fireEvent.change(screen.getByPlaceholderText("123456"), { target: { value: "654321" } });
    const verifyButtons = screen.getAllByText("Activar MFA");
    fireEvent.click(verifyButtons[verifyButtons.length - 1]);

    await waitFor(() => expect(verifyMfa).toHaveBeenCalledWith("654321"));
    expect(toastSuccess).toHaveBeenCalledWith("MFA activado exitosamente");
    await waitFor(() => expect(screen.queryByText("Reportar transacción")).not.toBeInTheDocument());
  });

  it("the MFA code input strips non-digit characters", async () => {
    render(<SecurityPage />);
    await screen.findByText("iPhone 15");
    fireEvent.click(screen.getByText("Activar MFA"));
    const codeInput = screen.getByPlaceholderText("123456");
    fireEvent.change(codeInput, { target: { value: "12a3b4" } });
    expect(codeInput).toHaveValue("1234");
  });
});
