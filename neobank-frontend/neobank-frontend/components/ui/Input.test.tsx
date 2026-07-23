// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Input from "./Input";

describe("Input", () => {
  it("renders the label and forwards value changes", () => {
    const onChange = vi.fn();
    render(<Input label="Email" value="" onChange={onChange} />);
    expect(screen.getByText("Email")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "a@b.com" } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it("shows an error message and the error border styling", () => {
    render(<Input error="Required" />);
    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("renders no label/error when omitted", () => {
    render(<Input placeholder="x" />);
    expect(screen.queryByText("Required")).not.toBeInTheDocument();
  });

  it("a password field starts masked and toggles to visible on click", () => {
    render(<Input type="password" value="secret" onChange={() => {}} />);
    const input = document.querySelector("input")!;
    expect(input).toHaveAttribute("type", "password");

    fireEvent.click(screen.getByRole("button"));
    expect(input).toHaveAttribute("type", "text");

    fireEvent.click(screen.getByRole("button"));
    expect(input).toHaveAttribute("type", "password");
  });

  it("a non-password field has no visibility toggle button", () => {
    render(<Input type="text" value="x" onChange={() => {}} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders a suffix for non-password fields", () => {
    render(<Input suffix={<span>MXN</span>} />);
    expect(screen.getByText("MXN")).toBeInTheDocument();
  });
});
