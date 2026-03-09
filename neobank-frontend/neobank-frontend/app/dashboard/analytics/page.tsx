"use client";
import { useEffect, useState } from "react";
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import { TrendingUp, TrendingDown, BarChart3 } from "lucide-react";
import { analytics } from "@/lib/api";
import { formatCurrency, cn } from "@/lib/utils";
import type { SpendingAnalytics } from "@/lib/types";
import Card from "@/components/ui/Card";

const PERIOD_OPTIONS = [
  { label: "Semana", value: "week" as const },
  { label: "Mes", value: "month" as const },
  { label: "Año", value: "year" as const },
];

const COLORS = ["#22d3ee", "#34d399", "#fbbf24", "#fb7185", "#a78bfa", "#f97316"];

const CustomTooltip = ({ active, payload, label }: { active?: boolean; payload?: { value: number; name: string }[]; label?: string }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="glass rounded-xl px-3 py-2 text-xs">
      <p className="text-slate-400 mb-1">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="text-slate-100 font-mono font-medium">{formatCurrency(p.value)}</p>
      ))}
    </div>
  );
};

export default function AnalyticsPage() {
  const [data, setData] = useState<SpendingAnalytics | null>(null);
  const [period, setPeriod] = useState<"week" | "month" | "year">("month");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    analytics.getSpending(period)
      .then(r => setData(r.data ?? null))
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [period]);

  // Mock data for empty state
  const mockDaily = Array.from({ length: 7 }, (_, i) => ({
    date: ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"][i],
    spent: Math.random() * 2000 + 500,
    received: Math.random() * 3000,
  }));

  const displayData = data?.dailyBreakdown ?? mockDaily;
  const displayCategories = data?.categories ?? [
    { category: "Restaurantes", amount: 3200, percentage: 28, count: 12 },
    { category: "Transporte", amount: 1800, percentage: 16, count: 22 },
    { category: "Supermercado", amount: 2800, percentage: 24, count: 8 },
    { category: "Entretenimiento", amount: 1500, percentage: 13, count: 5 },
    { category: "Servicios", amount: 900, percentage: 8, count: 3 },
    { category: "Otros", amount: 1200, percentage: 11, count: 9 },
  ];

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="font-display font-bold text-2xl text-slate-100">Análisis de gastos</h1>
        <div className="flex gap-1">
          {PERIOD_OPTIONS.map(opt => (
            <button
              key={opt.value}
              onClick={() => setPeriod(opt.value)}
              className={cn(
                "px-3 py-1.5 rounded-lg text-xs font-medium transition-all",
                period === opt.value
                  ? "bg-cyan-500/20 text-cyan-400 border border-cyan-500/30"
                  : "text-slate-400 hover:text-slate-200 border border-white/8"
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 gap-3">
        <Card className="text-center">
          <div className="flex items-center gap-2 justify-center mb-1">
            <TrendingDown size={16} className="text-rose-400" />
            <span className="text-xs text-slate-500">Total gastado</span>
          </div>
          <p className="font-display font-bold text-xl text-rose-400 font-mono">
            {formatCurrency(data?.totalSpent ?? 11400)}
          </p>
        </Card>
        <Card className="text-center">
          <div className="flex items-center gap-2 justify-center mb-1">
            <TrendingUp size={16} className="text-emerald-400" />
            <span className="text-xs text-slate-500">Total recibido</span>
          </div>
          <p className="font-display font-bold text-xl text-emerald-400 font-mono">
            {formatCurrency(data?.totalReceived ?? 18000)}
          </p>
        </Card>
      </div>

      {/* Line chart */}
      <Card>
        <h2 className="font-display font-semibold text-slate-100 text-sm mb-4">Flujo de dinero</h2>
        {loading ? (
          <div className="skeleton h-40 rounded-xl" />
        ) : (
          <ResponsiveContainer width="100%" height={160}>
            <LineChart data={displayData}>
              <XAxis dataKey="date" tick={{ fontSize: 10, fill: "#64748b" }} axisLine={false} tickLine={false} />
              <YAxis hide />
              <Tooltip content={<CustomTooltip />} />
              <Line type="monotone" dataKey="spent" stroke="#fb7185" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="received" stroke="#34d399" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
        <div className="flex gap-4 mt-2">
          <div className="flex items-center gap-1.5 text-xs text-slate-500">
            <span className="w-3 h-0.5 bg-emerald-400 inline-block rounded" /> Recibido
          </div>
          <div className="flex items-center gap-1.5 text-xs text-slate-500">
            <span className="w-3 h-0.5 bg-rose-400 inline-block rounded" /> Gastado
          </div>
        </div>
      </Card>

      {/* Categories */}
      <Card>
        <h2 className="font-display font-semibold text-slate-100 text-sm mb-4">Gastos por categoría</h2>
        {loading ? (
          <div className="space-y-2">{[1,2,3].map(i => <div key={i} className="skeleton h-10 rounded-xl" />)}</div>
        ) : (
          <div className="flex gap-6">
            {/* Pie */}
            <div className="shrink-0">
              <ResponsiveContainer width={120} height={120}>
                <PieChart>
                  <Pie data={displayCategories} cx="50%" cy="50%" innerRadius={30} outerRadius={55} paddingAngle={2} dataKey="amount">
                    {displayCategories.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>

            {/* Legend */}
            <div className="flex-1 space-y-2 min-w-0">
              {displayCategories.map((cat, i) => (
                <div key={cat.category} className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                  <span className="text-xs text-slate-400 flex-1 truncate">{cat.category}</span>
                  <span className="text-xs text-slate-500">{cat.percentage}%</span>
                  <span className="text-xs font-mono text-slate-300 shrink-0">{formatCurrency(cat.amount)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </Card>

      {/* Bar chart */}
      <Card>
        <h2 className="font-display font-semibold text-slate-100 text-sm mb-4">Gastos diarios</h2>
        {loading ? (
          <div className="skeleton h-32 rounded-xl" />
        ) : (
          <ResponsiveContainer width="100%" height={128}>
            <BarChart data={displayData} barSize={10}>
              <XAxis dataKey="date" tick={{ fontSize: 10, fill: "#64748b" }} axisLine={false} tickLine={false} />
              <YAxis hide />
              <Tooltip content={<CustomTooltip />} />
              <Bar dataKey="spent" fill="#22d3ee" radius={[4, 4, 0, 0]} opacity={0.8} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Card>
    </div>
  );
}
