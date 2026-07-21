// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import Badge from "./Badge";

describe("Badge", () => {
  it("renders its children", () => {
    render(<Badge>Active</Badge>);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("defaults to the info variant", () => {
    render(<Badge>Info</Badge>);
    expect(screen.getByText("Info")).toHaveClass("status-info");
  });

  it.each([
    ["success", "status-success"],
    ["pending", "status-pending"],
    ["failed", "status-failed"],
    ["info", "status-info"],
    ["warning", "status-pending"],
  ] as const)("variant=%s maps to class %s", (variant, expectedClass) => {
    render(<Badge variant={variant}>x</Badge>);
    expect(screen.getByText("x")).toHaveClass(expectedClass);
  });

  it("merges a custom className", () => {
    render(<Badge className="custom-class">x</Badge>);
    expect(screen.getByText("x")).toHaveClass("custom-class");
  });
});
