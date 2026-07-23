// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import SettingsPage from "./page";
import { useAuthStore } from "@/lib/store/authStore";

const { getSettings, updateProfile, updateSettings, deleteAccount } = vi.hoisted(() => ({
  getSettings: vi.fn(), updateProfile: vi.fn(), updateSettings: vi.fn(), deleteAccount: vi.fn(),
}));
vi.mock("@/lib/api/users", () => ({ usersApi: { getSettings, updateProfile, updateSettings, deleteAccount } }));

const { changePassword } = vi.hoisted(() => ({ changePassword: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ authApi: { changePassword } }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

const user = { id: "u1", email: "carlos@neobank.mx", fullName: "Carlos Perez", phone: "+525512345678", city: "CDMX", state: "CDMX", kycStatus: "APPROVED" };
const settings = { emailNotifications: true, pushNotifications: false, smsNotifications: true, language: "es-MX", currency: "MXN" };

beforeEach(() => {
  getSettings.mockReset().mockResolvedValue({ data: settings });
  updateProfile.mockReset().mockResolvedValue({ data: { ...user, fullName: "Carlos Updated" } });
  updateSettings.mockReset().mockResolvedValue({ data: settings });
  deleteAccount.mockReset().mockResolvedValue({ data: "ok" });
  changePassword.mockReset().mockResolvedValue({ data: {} });
  toastSuccess.mockReset();
  toastError.mockReset();
  useAuthStore.setState({
    user: user as never, isLoggedIn: true, isLoading: false,
    setUser: vi.fn(), logout: vi.fn(),
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SettingsPage", () => {
  it("prefills the profile form from the logged-in user", async () => {
    render(<SettingsPage />);
    expect(screen.getByDisplayValue("Carlos Perez")).toBeInTheDocument();
    expect(screen.getByDisplayValue("+525512345678")).toBeInTheDocument();
    expect(screen.getAllByDisplayValue("CDMX").length).toBe(2); // city and state
  });

  it("saving the profile calls updateProfile with the edited fields and updates the store", async () => {
    render(<SettingsPage />);
    fireEvent.change(screen.getByDisplayValue("Carlos Perez"), { target: { value: "Carlos Updated" } });
    fireEvent.click(screen.getByText("Guardar cambios"));

    await waitFor(() => expect(updateProfile).toHaveBeenCalledWith(expect.objectContaining({ fullName: "Carlos Updated" })));
    expect(toastSuccess).toHaveBeenCalledWith("Perfil actualizado");
    expect(useAuthStore.getState().setUser).toHaveBeenCalledWith({ ...user, fullName: "Carlos Updated" });
  });

  it("a failed profile save shows the server error", async () => {
    updateProfile.mockRejectedValue({ response: { data: { message: "Server error" } }, isAxiosError: true });
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Guardar cambios"));
    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Server error"));
  });

  it("renders the notification toggles once settings load, reflecting their initial state", async () => {
    render(<SettingsPage />);
    expect(await screen.findByText("Notificaciones por correo")).toBeInTheDocument();
    const toggles = document.querySelectorAll('button[class*="rounded-full"][class*="w-11"]');
    expect(toggles.length).toBe(3);
    expect(toggles[0].className).toContain("bg-blue-600"); // emailNotifications: true
    expect(toggles[1].className).toContain("bg-slate-700"); // pushNotifications: false
  });

  it("toggling a notification switch flips its state locally", async () => {
    render(<SettingsPage />);
    await screen.findByText("Notificaciones por correo");
    function toggles() { return document.querySelectorAll('button[class*="rounded-full"][class*="w-11"]'); }
    fireEvent.click(toggles()[1]); // enable push
    expect(toggles()[1].className).toContain("bg-blue-600");
  });

  it("saving preferences sends the current settings object", async () => {
    render(<SettingsPage />);
    await screen.findByText("Notificaciones por correo");
    fireEvent.click(screen.getByText("Guardar preferencias"));
    await waitFor(() => expect(updateSettings).toHaveBeenCalledWith(settings));
    expect(toastSuccess).toHaveBeenCalledWith("Ajustes guardados");
  });

  it("changes the language and currency selects", async () => {
    render(<SettingsPage />);
    await screen.findByText("Apariencia e idioma");
    fireEvent.change(screen.getByDisplayValue("Español (México)"), { target: { value: "en-US" } });
    fireEvent.click(screen.getByText("Guardar preferencias"));
    await waitFor(() => expect(updateSettings).toHaveBeenCalledWith(expect.objectContaining({ language: "en-US" })));
  });

  it("does not render the notifications/appearance cards before settings load", () => {
    getSettings.mockReturnValue(new Promise(() => {})); // never resolves
    render(<SettingsPage />);
    expect(screen.queryByText("Notificaciones por correo")).not.toBeInTheDocument();
    expect(screen.queryByText("Apariencia e idioma")).not.toBeInTheDocument();
  });

  it("Cambiar contraseña opens the modal; mismatched passwords are rejected client-side", async () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Cambiar contraseña"));
    expect(screen.getByText("Cambiar contraseña", { selector: "h3" })).toBeInTheDocument();

    const [current, next, confirm] = screen.getAllByDisplayValue("");
    fireEvent.change(current, { target: { value: "oldpass1" } });
    fireEvent.change(next,    { target: { value: "newpass1" } });
    fireEvent.change(confirm, { target: { value: "different1" } });
    fireEvent.click(screen.getByText("Actualizar contraseña"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Las contraseñas no coinciden"));
    expect(changePassword).not.toHaveBeenCalled();
  });

  it("a matching password change calls the API and closes the modal", async () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Cambiar contraseña"));
    const [current, next, confirm] = screen.getAllByDisplayValue("");
    fireEvent.change(current, { target: { value: "oldpass1" } });
    fireEvent.change(next,    { target: { value: "newpass1" } });
    fireEvent.change(confirm, { target: { value: "newpass1" } });
    fireEvent.click(screen.getByText("Actualizar contraseña"));

    await waitFor(() => expect(changePassword).toHaveBeenCalledWith("oldpass1", "newpass1"));
    expect(toastSuccess).toHaveBeenCalledWith("Contraseña actualizada");
    await waitFor(() => expect(screen.queryByText("Actualizar contraseña")).not.toBeInTheDocument());
  });

  it("clicking Salir calls the store's logout", async () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Salir"));
    expect(useAuthStore.getState().logout).toHaveBeenCalledTimes(1);
  });

  it("Eliminar cuenta requires a password before calling the API", async () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Eliminar"));
    fireEvent.click(screen.getByText("Eliminar mi cuenta"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Ingresa tu contraseña para confirmar"));
    expect(deleteAccount).not.toHaveBeenCalled();
  });

  it("deleting the account with a password calls the API and logs out", async () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Eliminar"));
    const deletePwInput = screen.getByText("Contraseña").parentElement!.querySelector("input")!;
    fireEvent.change(deletePwInput, { target: { value: "mypassword" } });
    fireEvent.click(screen.getByText("Eliminar mi cuenta"));

    await waitFor(() => expect(deleteAccount).toHaveBeenCalledWith("mypassword"));
    expect(toastSuccess).toHaveBeenCalledWith("Cuenta eliminada. Tienes 30 días para cancelar la eliminación.");
    expect(useAuthStore.getState().logout).toHaveBeenCalledTimes(1);
  });

  it("a failed account deletion shows the server error and does not log out", async () => {
    deleteAccount.mockRejectedValue({ response: { data: { message: "Wrong password" } }, isAxiosError: true });
    render(<SettingsPage />);
    fireEvent.click(screen.getByText("Eliminar"));
    const deletePwInput = screen.getByText("Contraseña").parentElement!.querySelector("input")!;
    fireEvent.change(deletePwInput, { target: { value: "wrong" } });
    fireEvent.click(screen.getByText("Eliminar mi cuenta"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Wrong password"));
    expect(useAuthStore.getState().logout).not.toHaveBeenCalled();
  });
});
