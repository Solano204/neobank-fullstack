import { describe, expect, it } from "vitest";
import { mapTransaction } from "./transactions";

describe("mapTransaction", () => {
  it("maps the Lambda's snake_case wire shape to the app's camelCase Transaction type", () => {
    const result = mapTransaction({
      transaction_id: "txn_123",
      from_account: "111111111111111111",
      to_account: "222222222222222222",
      amount: 500,
      currency: "MXN",
      status: "COMPLETED",
      type: "TRANSFER_OUT",
      description: "Rent",
      timestamp: 1700000000000,
    });

    expect(result.id).toBe("txn_123");
    expect(result.fromAccount).toBe("111111111111111111");
    expect(result.toAccount).toBe("222222222222222222");
    expect(result.amount).toBe(500);
    expect(result.type).toBe("TRANSFER");
    expect(result.createdAt).toBe(new Date(1700000000000).toISOString());
  });

  it("maps TRANSFER_IN to DEPOSIT so incoming transfers render distinctly", () => {
    const result = mapTransaction({
      from_account: "111111111111111111",
      to_account: "222222222222222222",
      amount: 500,
      status: "COMPLETED",
      type: "TRANSFER_IN",
    });

    expect(result.type).toBe("DEPOSIT");
  });

  it("always returns a positive amount regardless of the sign the query lambda applied", () => {
    const outgoing = mapTransaction({
      from_account: "a", to_account: "b", amount: -500, status: "COMPLETED", type: "TRANSFER_OUT",
    });
    const incoming = mapTransaction({
      from_account: "a", to_account: "b", amount: 500, status: "COMPLETED", type: "TRANSFER_IN",
    });

    expect(outgoing.amount).toBe(500);
    expect(incoming.amount).toBe(500);
  });

  it("falls back to the id field when transaction_id is missing", () => {
    const result = mapTransaction({
      id: "fallback-id",
      from_account: "a", to_account: "b", amount: 1, status: "COMPLETED",
    });

    expect(result.id).toBe("fallback-id");
  });
});
