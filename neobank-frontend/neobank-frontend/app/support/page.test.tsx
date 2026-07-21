// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import SupportPage from "./page";

const { sendChat } = vi.hoisted(() => ({ sendChat: vi.fn() }));
vi.mock("@/lib/api/support", () => ({ supportApi: { sendChat } }));

beforeEach(() => {
  sendChat.mockReset().mockResolvedValue({ data: { message: "Tu saldo es $1,000.00", sessionId: "sess-1" } });
  // jsdom doesn't implement scrollIntoView; the page calls it on every message update.
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SupportPage", () => {
  it("shows the assistant's greeting message on load", () => {
    render(<SupportPage />);
    expect(screen.getByText(/¡Hola! Soy el asistente de NeoBank/)).toBeInTheDocument();
    expect(screen.getByText("NeoBank Assistant")).toBeInTheDocument();
  });

  it("the send button is disabled with empty input", () => {
    render(<SupportPage />);
    const sendButton = document.querySelector('button[class*="px-4"]') as HTMLButtonElement;
    expect(sendButton).toBeDisabled();
  });

  it("typing enables the send button, and sending appends the user message then the bot reply", async () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");
    fireEvent.change(input, { target: { value: "¿Cuánto tengo disponible?" } });

    const sendButton = document.querySelector('button[class*="px-4"]') as HTMLButtonElement;
    expect(sendButton).not.toBeDisabled();
    fireEvent.click(sendButton);

    expect(await screen.findByText("¿Cuánto tengo disponible?")).toBeInTheDocument();
    expect(await screen.findByText("Tu saldo es $1,000.00")).toBeInTheDocument();
    expect(sendChat).toHaveBeenCalledWith("¿Cuánto tengo disponible?", undefined);
  });

  it("clears the input immediately after sending", async () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "hola" } });
    fireEvent.click(document.querySelector('button[class*="px-4"]')!);
    expect(input.value).toBe("");
  });

  it("pressing Enter (without shift) sends the message", async () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");
    fireEvent.change(input, { target: { value: "hola" } });
    fireEvent.keyDown(input, { key: "Enter", shiftKey: false });

    await waitFor(() => expect(sendChat).toHaveBeenCalledWith("hola", undefined));
  });

  it("shift+Enter does not send the message", () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");
    fireEvent.change(input, { target: { value: "hola" } });
    fireEvent.keyDown(input, { key: "Enter", shiftKey: true });
    expect(sendChat).not.toHaveBeenCalled();
  });

  it("reuses the sessionId from the first response on subsequent messages", async () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");

    fireEvent.change(input, { target: { value: "primero" } });
    fireEvent.click(document.querySelector('button[class*="px-4"]')!);
    await waitFor(() => expect(sendChat).toHaveBeenCalledWith("primero", undefined));

    fireEvent.change(input, { target: { value: "segundo" } });
    fireEvent.click(document.querySelector('button[class*="px-4"]')!);
    await waitFor(() => expect(sendChat).toHaveBeenCalledWith("segundo", "sess-1"));
  });

  it("clicking a quick-prompt chip fills the input without sending", () => {
    render(<SupportPage />);
    fireEvent.click(screen.getByText("Reportar fraude"));
    const input = screen.getByPlaceholderText("Escribe tu pregunta…") as HTMLInputElement;
    expect(input.value).toBe("Reportar fraude");
    expect(sendChat).not.toHaveBeenCalled();
  });

  it("a chat error is shown inline as an assistant message instead of a toast", async () => {
    sendChat.mockRejectedValue({ response: { data: { message: "Service unavailable" } }, isAxiosError: true });
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");
    fireEvent.change(input, { target: { value: "hola" } });
    fireEvent.click(document.querySelector('button[class*="px-4"]')!);

    expect(await screen.findByText("Service unavailable")).toBeInTheDocument();
  });

  it("does not send an empty or whitespace-only message", () => {
    render(<SupportPage />);
    const input = screen.getByPlaceholderText("Escribe tu pregunta…");
    fireEvent.change(input, { target: { value: "   " } });
    const sendButton = document.querySelector('button[class*="px-4"]') as HTMLButtonElement;
    expect(sendButton).toBeDisabled();
  });
});
