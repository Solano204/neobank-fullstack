"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import { analyticsApi } from "@/lib/api/analytics";
import type { SpendingAnalytics } from "@/types";
import { formatMXN } from "@/lib/utils";
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import { TrendingUp, TrendingDown, BarChart3 } from "lucide-react";

const PERIODS = [["WEEK","Semana"],["MONTH","Mes"],["QUARTER","3 meses"],["YEAR","Año"]];

export default function AnalyticsPage() {
  const [data, setData]   = useState<SpendingAnalytics | null>(null);
  const [period, setPeriod] = useState("MONTH");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    analyticsApi.getSpending(period).then(r => setData(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, [period]);

  return (
    <>
      <Header title="Análisis" />
      <div className="flex-1 p-6 animate-fade-in">
        {/* Period selector */}
        <div className="flex gap-2 mb-6">
          {PERIODS.map(([v, l]) => (
            <button key={v} onClick={() => setPeriod(v)}
              className={`px-4 py-2 rounded-xl text-sm font-medium transition-all ${period === v ? "bg-blue-600 text-white" : "bg-[#13131f] border border-[#1e1e30] text-slate-400 hover:text-white"}`}>{l}</button>
          ))}
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-48"><div className="w-7 h-7 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : data ? (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Summary */}
            <Card className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-red-500/10 flex items-center justify-center"><TrendingDown size={22} className="text-red-400" /></div>
              <div><p className="text-xs text-slate-500">Total gastado</p><p className="font-display text-xl font-bold text-white">{formatMXN(data.totalSpent)}</p></div>
            </Card>
            <Card className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-emerald-500/10 flex items-center justify-center"><TrendingUp size={22} className="text-emerald-400" /></div>
              <div><p className="text-xs text-slate-500">Total recibido</p><p className="font-display text-xl font-bold text-white">{formatMXN(data.totalReceived)}</p></div>
            </Card>
            <Card className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-blue-500/10 flex items-center justify-center"><BarChart3 size={22} className="text-blue-400" /></div>
              <div><p className="text-xs text-slate-500">Balance neto</p><p className={`font-display text-xl font-bold ${data.totalReceived - data.totalSpent >= 0 ? "text-emerald-400" : "text-red-400"}`}>{formatMXN(data.totalReceived - data.totalSpent)}</p></div>
            </Card>

            {/* Area chart */}
            <Card className="lg:col-span-2">
              <h3 className="font-display font-semibold text-white mb-4">Flujo mensual</h3>
              <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={data.monthlyData}>
                  <defs>
                    <linearGradient id="spentGrad" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#ef4444" stopOpacity={0.2}/><stop offset="95%" stopColor="#ef4444" stopOpacity={0}/></linearGradient>
                    <linearGradient id="recvGrad"  x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#10b981" stopOpacity={0.2}/><stop offset="95%" stopColor="#10b981" stopOpacity={0}/></linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e1e30" />
                  <XAxis dataKey="month" tick={{ fill: "#475569", fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: "#475569", fontSize: 11 }} axisLine={false} tickLine={false} tickFormatter={v => `$${(v/1000).toFixed(0)}k`} />
                  <Tooltip contentStyle={{ background: "#13131f", border: "1px solid #1e1e30", borderRadius: "8px", color: "#f1f5f9" }} formatter={(v: number) => formatMXN(v)} />
                  <Area type="monotone" dataKey="spent" name="Gasto" stroke="#ef4444" fill="url(#spentGrad)" strokeWidth={2} />
                  <Area type="monotone" dataKey="received" name="Ingreso" stroke="#10b981" fill="url(#recvGrad)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            </Card>

            {/* Pie chart */}
            <Card>
              <h3 className="font-display font-semibold text-white mb-4">Por categoría</h3>
              {data.categories.length > 0 ? (
                <>
                  <ResponsiveContainer width="100%" height={160}>
                    <PieChart>
                      <Pie data={data.categories} cx="50%" cy="50%" innerRadius={45} outerRadius={70} dataKey="amount" paddingAngle={3}>
                        {data.categories.map((cat, i) => <Cell key={i} fill={cat.color || `hsl(${i*60},70%,55%)`} />)}
                      </Pie>
                      <Tooltip contentStyle={{ background: "#13131f", border: "1px solid #1e1e30", borderRadius: "8px" }} formatter={(v: number) => formatMXN(v)} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="flex flex-col gap-2 mt-3">
                    {data.categories.slice(0, 4).map((cat, i) => (
                      <div key={i} className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: cat.color || `hsl(${i*60},70%,55%)` }} />
                          <span className="text-slate-400">{cat.name}</span>
                        </div>
                        <span className="text-white font-medium">{cat.percentage.toFixed(0)}%</span>
                      </div>
                    ))}
                  </div>
                </>
              ) : <p className="text-center text-slate-500 text-sm py-8">Sin datos de categorías</p>}
            </Card>
          </div>
        ) : (
          <Card className="text-center py-12 text-slate-500">
            <BarChart3 size={36} className="mx-auto mb-3 opacity-30" />
            <p className="text-sm">No hay datos de análisis disponibles</p>
          </Card>
        )}
      </div>
    </>
  );
}
