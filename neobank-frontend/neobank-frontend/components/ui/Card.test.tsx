// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Card from "./Card";

describe("Card", () => {
  it("renders its children", () => {
    render(<Card>Content</Card>);
    expect(screen.getByText("Content")).toBeInTheDocument();
  });

  it("is not clickable-styled and has no click handler when onClick is omitted", () => {
    render(<Card>Content</Card>);
    expect(screen.getByText("Content")).not.toHaveClass("cursor-pointer");
  });

  it("becomes clickable and fires onClick when provided", () => {
    const onClick = vi.fn();
    render(<Card onClick={onClick}>Content</Card>);
    const card = screen.getByText("Content");
    expect(card).toHaveClass("cursor-pointer");
    fireEvent.click(card);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
