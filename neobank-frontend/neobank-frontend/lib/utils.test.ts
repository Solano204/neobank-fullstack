import { describe, expect, it } from "vitest";
import { cn, formatCLABE, formatMXN, formatDate, formatDateShort, maskAccount, getInitials, getTransactionSign } from "./utils";

describe("cn", () => {
  it("merges class name strings", () => {
    expect(cn("a", "b")).toBe("a b");
  });

  it("drops falsy values", () => {
    expect(cn("a", false && "b", undefined, "c")).toBe("a c");
  });

  it("resolves conflicting Tailwind utilities, keeping the last one (tailwind-merge)", () => {
    expect(cn("p-2", "p-4")).toBe("p-4");
  });
});

describe("formatMXN", () => {
  it("formats a positive amount as MXN currency", () => {
    expect(formatMXN(1234.5)).toBe("$1,234.50");
  });

  it("always shows two decimal places", () => {
    expect(formatMXN(100)).toBe("$100.00");
  });

  it("formats zero", () => {
    expect(formatMXN(0)).toBe("$0.00");
  });
});

describe("formatDate", () => {
  it("includes day, month, year, hour and minute", () => {
    const result = formatDate("2026-01-15T10:30:00Z");
    expect(result).toMatch(/15/);
    expect(result).toMatch(/2026/);
  });
});

describe("formatDateShort", () => {
  it("includes only day and month, not the year", () => {
    const result = formatDateShort("2026-01-15T10:30:00Z");
    expect(result).toMatch(/15/);
    expect(result).not.toMatch(/2026/);
  });
});

describe("getInitials", () => {
  it("takes the first letter of the first two words, uppercased", () => {
    expect(getInitials("Carlos Perez")).toBe("CP");
  });

  it("a single-word name yields one letter", () => {
    expect(getInitials("Carlos")).toBe("C");
  });

  it("ignores a third+ word", () => {
    expect(getInitials("Carlos De La Torre")).toBe("CD");
  });
});

describe("formatCLABE", () => {
  it("groups an 18-digit CLABE into 4-digit chunks", () => {
    expect(formatCLABE("123456789012345678")).toBe("1234 5678 9012 3456 78");
  });
});

describe("maskAccount", () => {
  it("keeps only the last 4 digits visible", () => {
    expect(maskAccount("123456789012345678")).toBe("•••• 5678");
  });

  it("returns an empty string for an empty account", () => {
    expect(maskAccount("")).toBe("");
  });
});

describe("getTransactionSign", () => {
  it("is always positive for a deposit", () => {
    expect(getTransactionSign("DEPOSIT", false)).toBe("+");
  });

  it("is always negative for a withdrawal", () => {
    expect(getTransactionSign("WITHDRAWAL", true)).toBe("-");
  });

  it("for a plain transfer, depends on which side of it the account is on", () => {
    expect(getTransactionSign("TRANSFER", true)).toBe("-");
    expect(getTransactionSign("TRANSFER", false)).toBe("+");
  });
});
