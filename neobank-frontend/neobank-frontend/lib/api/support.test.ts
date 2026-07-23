import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { supportApi } from "./support";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("supportApi", () => {
  it("sendChat POSTs message/session_id and maps bot_response/session_id back to camelCase", async () => {
    mock.onPost("/api/support/chat").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ message: "hi", session_id: "sess-1" });
      return [200, { data: { bot_response: "Hello!", session_id: "sess-1" } }];
    });

    const res = await supportApi.sendChat("hi", "sess-1");

    expect(res.data).toEqual({ message: "Hello!", sessionId: "sess-1" });
  });

  it("sendChat with no sessionId sends session_id: undefined", async () => {
    mock.onPost("/api/support/chat").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ message: "hi" });
      return [200, { data: { bot_response: "Hello!", session_id: "new-sess" } }];
    });
    await supportApi.sendChat("hi");
  });

  it("getFaq GETs /api/support/faq", async () => {
    mock.onGet("/api/support/faq").reply(200, { data: { categories: [] } });
    const res = await supportApi.getFaq();
    expect(res.data.categories).toEqual([]);
  });

  it("createTicket POSTs subject/description/priority", async () => {
    mock.onPost("/api/support/ticket").reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ subject: "Card issue", description: "Declined", priority: "HIGH" });
      return [201, { data: { id: "t1" } }];
    });
    await supportApi.createTicket("Card issue", "Declined", "HIGH");
  });

  it("getTickets GETs /api/support/tickets", async () => {
    mock.onGet("/api/support/tickets").reply(200, { data: { tickets: [] } });
    const res = await supportApi.getTickets();
    expect(res.data.tickets).toEqual([]);
  });
});
