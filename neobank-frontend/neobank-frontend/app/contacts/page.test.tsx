// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ContactsPage from "./page";

const { getAllContacts, add, remove, toggleFav } = vi.hoisted(() => ({
  getAllContacts: vi.fn(),
  add: vi.fn(),
  remove: vi.fn(),
  toggleFav: vi.fn(),
}));
vi.mock("@/lib/api/contacts", () => ({ contactsApi: { getAll: getAllContacts, add, remove, toggleFav } }));

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

const favContact = { id: "c1", name: "Ana Lopez", accountNumber: "111111111111111111", nickname: "Ana", favorite: true, createdAt: "" };
const plainContact = { id: "c2", name: "Beto Cruz", accountNumber: "222222222222222222", nickname: "", favorite: false, createdAt: "" };

beforeEach(() => {
  getAllContacts.mockReset().mockResolvedValue({ data: { contacts: [favContact, plainContact] } });
  add.mockReset();
  remove.mockReset().mockResolvedValue({ data: {} });
  toggleFav.mockReset().mockResolvedValue({ data: {} });
  push.mockReset();
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ContactsPage", () => {
  it("shows a spinner while loading, then splits contacts into Favoritos / Todos los contactos", async () => {
    render(<ContactsPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("Favoritos")).toBeInTheDocument();
    expect(screen.getByText("Todos los contactos")).toBeInTheDocument();
    expect(screen.getByText("Ana")).toBeInTheDocument();
    expect(screen.getByText("Beto Cruz")).toBeInTheDocument(); // falls back to `name` when nickname is empty
  });

  it('shows the empty state with "Sin contactos guardados" when there are none', async () => {
    getAllContacts.mockResolvedValue({ data: { contacts: [] } });
    render(<ContactsPage />);
    expect(await screen.findByText("Sin contactos guardados")).toBeInTheDocument();
    expect(screen.queryByText("Favoritos")).not.toBeInTheDocument();
  });

  it("omits the Favoritos section entirely when no contact is favorited", async () => {
    getAllContacts.mockResolvedValue({ data: { contacts: [plainContact] } });
    render(<ContactsPage />);
    await screen.findByText("Todos los contactos");
    expect(screen.queryByText("Favoritos")).not.toBeInTheDocument();
  });

  it("masks the account number to its last 4 digits", async () => {
    render(<ContactsPage />);
    expect(await screen.findByText("••••1111")).toBeInTheDocument();
  });

  it('clicking the Send icon navigates to /transfer prefilled with the contact\'s account', async () => {
    render(<ContactsPage />);
    await screen.findByText("Ana");
    const sendButton = screen.getAllByTitle("Transferir")[0];
    fireEvent.click(sendButton);
    expect(push).toHaveBeenCalledWith("/transfer?to=111111111111111111");
  });

  it("toggling favorite calls the API with the inverted flag and updates the UI", async () => {
    render(<ContactsPage />);
    await screen.findByText("Beto Cruz");
    const row = screen.getByText("Beto Cruz").closest(".card-neo")!;
    const starButton = row.querySelectorAll("button")[1];
    fireEvent.click(starButton);
    await waitFor(() => expect(toggleFav).toHaveBeenCalledWith("c2", true));
  });

  it("removing a contact calls the API and shows a success toast", async () => {
    render(<ContactsPage />);
    await screen.findByText("Beto Cruz");
    const row = screen.getByText("Beto Cruz").closest(".card-neo")!;
    const deleteButton = row.querySelectorAll("button")[2];
    fireEvent.click(deleteButton);
    await waitFor(() => expect(remove).toHaveBeenCalledWith("c2"));
    expect(toastSuccess).toHaveBeenCalledWith("Contacto eliminado");
  });

  it("opens the add-contact modal from the header button", async () => {
    render(<ContactsPage />);
    await screen.findByText("Ana");
    fireEvent.click(screen.getByText("Agregar contacto"));
    expect(screen.getByText("Nuevo contacto")).toBeInTheDocument();
  });

  it("rejects a CLABE shorter than 18 digits without calling the API", async () => {
    render(<ContactsPage />);
    await screen.findByText("Ana");
    fireEvent.click(screen.getByText("Agregar contacto"));
    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "12345" } });
    fireEvent.click(screen.getByText("Guardar contacto"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("CLABE debe tener 18 dígitos"));
    expect(add).not.toHaveBeenCalled();
  });

  it("the CLABE input strips non-digit characters and caps at 18", async () => {
    render(<ContactsPage />);
    await screen.findByText("Ana");
    fireEvent.click(screen.getByText("Agregar contacto"));
    const input = screen.getByPlaceholderText("000000000000000000");
    fireEvent.change(input, { target: { value: "abc333333333333333333999" } });
    expect(input).toHaveValue("333333333333333333");
  });

  it("adding a valid contact calls the API, appends it to the list, and closes the modal", async () => {
    add.mockResolvedValue({ data: { id: "c3", name: "Nuevo", accountNumber: "333333333333333333", nickname: "Nueva", favorite: false, createdAt: "" } });
    render(<ContactsPage />);
    await screen.findByText("Ana");
    fireEvent.click(screen.getByText("Agregar contacto"));
    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "333333333333333333" } });
    fireEvent.change(screen.getByPlaceholderText("Mamá, Arrendador…"), { target: { value: "Nueva" } });
    fireEvent.click(screen.getByText("Guardar contacto"));

    await waitFor(() => expect(add).toHaveBeenCalledWith("333333333333333333", "Nueva"));
    expect(await screen.findByText("Nueva")).toBeInTheDocument();
    expect(screen.queryByText("Nuevo contacto")).not.toBeInTheDocument(); // modal closed
    expect(toastSuccess).toHaveBeenCalledWith("Contacto agregado");
  });

  it("a failed add shows the server error and keeps the modal open", async () => {
    add.mockRejectedValue({ response: { data: { message: "Cuenta no encontrada" } }, isAxiosError: true });
    render(<ContactsPage />);
    await screen.findByText("Ana");
    fireEvent.click(screen.getByText("Agregar contacto"));
    fireEvent.change(screen.getByPlaceholderText("000000000000000000"), { target: { value: "999999999999999999" } });
    fireEvent.click(screen.getByText("Guardar contacto"));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Cuenta no encontrada"));
    expect(screen.getByText("Nuevo contacto")).toBeInTheDocument();
  });

  it('"Agregar primero" in the empty state also opens the modal', async () => {
    getAllContacts.mockResolvedValue({ data: { contacts: [] } });
    render(<ContactsPage />);
    fireEvent.click(await screen.findByText("Agregar primero"));
    expect(screen.getByText("Nuevo contacto")).toBeInTheDocument();
  });
});
