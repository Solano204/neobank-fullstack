// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Modal from "./Modal";

afterEach(() => {
  document.body.style.overflow = "";
});

describe("Modal", () => {
  it("renders nothing when closed", () => {
    const { container } = render(<Modal open={false} onClose={vi.fn()}>x</Modal>);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders its title and children when open", () => {
    render(<Modal open onClose={vi.fn()} title="Confirm">Are you sure?</Modal>);
    expect(screen.getByText("Confirm")).toBeInTheDocument();
    expect(screen.getByText("Are you sure?")).toBeInTheDocument();
  });

  it("locks body scroll while open, restores it on close", () => {
    const { rerender } = render(<Modal open onClose={vi.fn()}>x</Modal>);
    expect(document.body.style.overflow).toBe("hidden");

    rerender(<Modal open={false} onClose={vi.fn()}>x</Modal>);
    expect(document.body.style.overflow).toBe("");
  });

  it("clicking the backdrop calls onClose", () => {
    const onClose = vi.fn();
    const { container } = render(<Modal open onClose={onClose}>x</Modal>);
    fireEvent.click(container.querySelector(".absolute.inset-0")!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("clicking the X button calls onClose", () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose}>x</Modal>);
    fireEvent.click(screen.getByRole("button"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("clicking inside the modal content does not call onClose", () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose}>Some content</Modal>);
    fireEvent.click(screen.getByText("Some content"));
    expect(onClose).not.toHaveBeenCalled();
  });
});
