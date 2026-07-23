// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import AnalyticsPage from "./page";

const { getSpending } = vi.hoisted(() => ({ getSpending: vi.fn() }));
vi.mock("@/lib/api/analytics", () => ({ analyticsApi: { getSpending } }));

// recharts' ResponsiveContainer needs a real ResizeObserver/layout to render
// children in jsdom - not present here (no polyfill in vitest.setup.ts) and
// not worth adding just for this page. Stub the library with passthrough
// components so we test the page's own logic, not recharts' internals.
vi.mock("recharts", () => {
  const Passthrough = ({ children }: { children?: unknown }) => <div>{children as never}</div>;
  return {
    AreaChart: Passthrough, Area: () => null, XAxis: () => null, YAxis: () => null,
    CartesianGrid: () => null, Tooltip: () => null, ResponsiveContainer: Passthrough,
    PieChart: Passthrough, Pie: () => null, Cell: () => null,
  };
});

const analytics = {
  totalSpent: 3000, totalReceived: 5000,
  monthlyData: [{ month: "Ene", spent: 1000, received: 2000 }],
  categories: [
    { name: "Comida", amount: 1500, percentage: 50, color: "#ef4444" },
    { name: "Transporte", amount: 1500, percentage: 50, color: "#10b981" },
  ],
};

beforeEach(() => {
  getSpending.mockReset().mockResolvedValue({ data: analytics });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("AnalyticsPage", () => {
  it("shows a spinner while loading, then the summary cards", async () => {
    render(<AnalyticsPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("Total gastado")).toBeInTheDocument();
    expect(screen.getByText("$3,000.00")).toBeInTheDocument();
    expect(screen.getByText("Total recibido")).toBeInTheDocument();
    expect(screen.getByText("$5,000.00")).toBeInTheDocument();
  });

  it("computes and colors the net balance", async () => {
    render(<AnalyticsPage />);
    await screen.findByText("Balance neto");
    const net = screen.getByText("$2,000.00"); // 5000 - 3000
    expect(net).toHaveClass("text-emerald-400");
  });

  it("colors a negative net balance red", async () => {
    getSpending.mockResolvedValue({ data: { ...analytics, totalSpent: 6000, totalReceived: 5000 } });
    render(<AnalyticsPage />);
    await screen.findByText("Balance neto");
    const net = screen.getByText("-$1,000.00");
    expect(net).toHaveClass("text-red-400");
  });

  it("defaults to the MONTH period and requests it on load", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(getSpending).toHaveBeenCalledWith("MONTH"));
    expect(await screen.findByText("Mes")).toHaveClass("bg-blue-600");
  });

  it("switching periods re-fetches with the new period", async () => {
    render(<AnalyticsPage />);
    await screen.findByText("Total gastado");
    fireEvent.click(screen.getByText("Año"));
    await waitFor(() => expect(getSpending).toHaveBeenCalledWith("YEAR"));
    expect(screen.getByText("Año")).toHaveClass("bg-blue-600");
  });

  it("shows the category breakdown with percentages", async () => {
    render(<AnalyticsPage />);
    expect(await screen.findByText("Comida")).toBeInTheDocument();
    expect(screen.getAllByText("50%").length).toBe(2);
  });

  it('shows "Sin datos de categorías" when categories is empty', async () => {
    getSpending.mockResolvedValue({ data: { ...analytics, categories: [] } });
    render(<AnalyticsPage />);
    expect(await screen.findByText("Sin datos de categorías")).toBeInTheDocument();
  });

  it('shows "No hay datos de análisis disponibles" when the API returns nothing', async () => {
    getSpending.mockResolvedValue({ data: null });
    render(<AnalyticsPage />);
    expect(await screen.findByText("No hay datos de análisis disponibles")).toBeInTheDocument();
  });

  it("degrades gracefully (stops loading) if the API call fails", async () => {
    getSpending.mockRejectedValue(new Error("network"));
    render(<AnalyticsPage />);
    await waitFor(() => expect(document.querySelector(".animate-spin")).not.toBeInTheDocument());
    expect(screen.getByText("No hay datos de análisis disponibles")).toBeInTheDocument();
  });

  it("shows only the first 4 categories in the legend list", async () => {
    const many = Array.from({ length: 6 }, (_, i) => ({ name: `Cat${i}`, amount: 100, percentage: 16.6, color: "#fff" }));
    getSpending.mockResolvedValue({ data: { ...analytics, categories: many } });
    render(<AnalyticsPage />);
    await screen.findByText("Cat0");
    expect(screen.getByText("Cat3")).toBeInTheDocument();
    expect(screen.queryByText("Cat4")).not.toBeInTheDocument();
  });
});
