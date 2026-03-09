"use client";
import { useEffect, useState, useCallback } from "react";
import { Search, Filter, ArrowDownLeft, ArrowUpRight, Download } from "lucide-react";
import { transactions, accounts } from "@/lib/api";
import { formatCurrency, formatDateTime, cn } from "@/lib/utils";
import type { Transaction, Account } from "@/lib/types";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

const FILTERS = [
  { label: "Todos", value: "" },
  { label: "Transferencias", value: "TRANSFER" },
  { label: "Depósitos", value: "DEPOSIT" },
  { label: "Retiros", value: "WITHDRAWAL" },
];

const STATUS_FILTERS = [
  { label: "Todos", value: "" },
  { label: "Completados", value: "COMPLETED" },
  { label: "Pendientes", value: "PENDING" },
  { label: "Fallidos", value: "FAILED" },
];

export default function TransactionsPage() {
  const [txs, setTxs] = useState<Transaction[]>([]);
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);

  const load = useCallback(async (reset = false) => {
    setLoading(true);
    try {
      const res = await transactions.getHistory({
        page: reset ? 1 : page,
        limit: 20,
        type: typeFilter || undefined,
        status: statusFilter || undefined,
      });
      const items = res.data?.items ?? [];
      setTxs(prev => reset ? items : [...prev, ...items]);
      setHasMore(res.data?.hasMore ?? false);
      if (reset) setPage(1);
    } catch { } finally {
      setLoading(false);
    }
  }, [page, typeFilter, statusFilter]);

  useEffect(() => { load(true); }, [typeFilter, statusFilter]);

  useEffect(() => {
    accounts.getAll().then(r => setAccount(r.data?.[0] ?? null)).catch(() => {});
  }, []);

  const filtered = txs.filter(tx =>
    search === "" ||
    tx.description?.toLowerCase().includes(search.toLowerCase()) ||
    tx.id.includes(search) ||
    tx.toAccount.includes(search) ||
    tx.fromAccount.includes(search)
  );

  const isCredit = (tx: Transaction) =>
    tx.type === "DEPOSIT" || tx.toAccount === account?.accountNumber;

  return (
    <div className="max-w-2xl mx-auto space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="font-display font-bold text-2xl text-slate-100">Movimientos</h1>
        <Button variant="secondary" size="sm">
          <Download size={14} /> Exportar
        </Button>
      </div>

      {/* Search */}
      <div className="relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
        <input
          type="text"
          placeholder="Buscar por descripción, cuenta..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full h-11 bg-navy-800/80 border border-white/10 rounded-xl text-slate-100 pl-10 pr-4 text-sm focus:border-cyan-500/60 transition-all placeholder-slate-600"
        />
      </div>

      {/* Type filter */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setTypeFilter(f.value)}
            className={cn(
              "px-3 py-1.5 rounded-lg text-sm font-medium whitespace-nowrap transition-all",
              typeFilter === f.value
                ? "bg-cyan-500/20 text-cyan-400 border border-cyan-500/30"
                : "bg-navy-800 text-slate-400 border border-white/8 hover:border-white/15"
            )}
          >
            {f.label}
          </button>
        ))}
        <div className="w-px bg-white/10 mx-1" />
        {STATUS_FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setStatusFilter(f.value)}
            className={cn(
              "px-3 py-1.5 rounded-lg text-sm font-medium whitespace-nowrap transition-all",
              statusFilter === f.value
                ? "bg-cyan-500/20 text-cyan-400 border border-cyan-500/30"
                : "bg-navy-800 text-slate-400 border border-white/8 hover:border-white/15"
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Transactions list */}
      <Card className="p-0 overflow-hidden">
        {loading && txs.length === 0 ? (
          <div className="space-y-px">
            {[1,2,3,4,5].map(i => (
              <div key={i} className="flex items-center gap-3 p-4">
                <div className="skeleton w-10 h-10 rounded-xl shrink-0" />
                <div className="flex-1 space-y-2">
                  <div className="skeleton h-3 w-32 rounded" />
                  <div className="skeleton h-2.5 w-24 rounded" />
                </div>
                <div className="skeleton h-4 w-20 rounded" />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-12">
            <Filter size={32} className="text-slate-700 mx-auto mb-3" />
            <p className="text-slate-500">No se encontraron movimientos</p>
          </div>
        ) : (
          <div>
            {filtered.map((tx, idx) => (
              <div
                key={tx.id}
                className={cn(
                  "flex items-center gap-3 p-4 hover:bg-white/3 cursor-pointer transition-colors",
                  idx !== 0 && "border-t border-white/5"
                )}
              >
                <div className={cn(
                  "w-10 h-10 rounded-xl flex items-center justify-center shrink-0",
                  isCredit(tx) ? "bg-emerald-400/10" : "bg-rose-400/10"
                )}>
                  {isCredit(tx)
                    ? <ArrowDownLeft size={18} className="text-emerald-400" />
                    : <ArrowUpRight size={18} className="text-rose-400" />}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-slate-100 font-medium truncate">
                    {tx.description ?? tx.type}
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5 font-mono">
                    {isCredit(tx) ? `De: ${tx.fromAccount.slice(-6)}` : `Para: ${tx.toAccount.slice(-6)}`}
                  </p>
                  <p className="text-xs text-slate-600">{formatDateTime(tx.createdAt)}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className={cn(
                    "text-sm font-semibold font-mono",
                    isCredit(tx) ? "text-emerald-400" : "text-rose-400"
                  )}>
                    {isCredit(tx) ? "+" : "-"}{formatCurrency(tx.amount)}
                  </p>
                  <span className={cn(
                    "text-xs px-1.5 py-0.5 rounded-md",
                    tx.status === "COMPLETED" ? "bg-emerald-500/10 text-emerald-400" :
                    tx.status === "PENDING" ? "bg-amber-500/10 text-amber-400" :
                    "bg-rose-500/10 text-rose-400"
                  )}>
                    {tx.status === "COMPLETED" ? "✓" : tx.status === "PENDING" ? "…" : "✗"}
                  </span>
                </div>
              </div>
            ))}

            {hasMore && (
              <div className="p-4 border-t border-white/5">
                <Button variant="secondary" size="sm" className="w-full" loading={loading} onClick={() => { setPage(p => p + 1); load(); }}>
                  Cargar más
                </Button>
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}
