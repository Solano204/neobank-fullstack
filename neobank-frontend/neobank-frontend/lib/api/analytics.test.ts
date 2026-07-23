import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { analyticsApi } from "./analytics";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("analyticsApi", () => {
  it("getSpending with no period sends no params query string", async () => {
    mock.onGet("/api/analytics/spending").reply((config) => {
      expect(config.params).toBeUndefined();
      return [200, { data: {} }];
    });
    await analyticsApi.getSpending();
  });

  it("getSpending with a period passes it as a query param", async () => {
    mock.onGet("/api/analytics/spending").reply((config) => {
      expect(config.params).toEqual({ period: "week" });
      return [200, { data: {} }];
    });
    await analyticsApi.getSpending("week");
  });

  it("getBalanceForecast GETs /api/analytics/balance-forecast", async () => {
    mock.onGet("/api/analytics/balance-forecast").reply(200, { data: { forecast_7_days: 100 } });
    const res = await analyticsApi.getBalanceForecast();
    expect(res.data.forecast_7_days).toBe(100);
  });
});
