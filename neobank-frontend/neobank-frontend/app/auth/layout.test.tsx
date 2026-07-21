// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import AuthLayout from "./layout";

describe("AuthLayout", () => {
  it("renders its children", () => {
    render(<AuthLayout><div>Login form</div></AuthLayout>);
    expect(screen.getByText("Login form")).toBeInTheDocument();
  });
});
