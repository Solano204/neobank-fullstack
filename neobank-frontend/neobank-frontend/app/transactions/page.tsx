"use client";
import { useState, useEffect, useCallback } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Badge  from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { transactionsApi } from "@/lib/api/transactions";
import { formatMXN, formatDate } from "@/lib/utils";
import type { Transaction } from "@/types";
import { ArrowDownLeft, ArrowUpRight, Search, Filter, ChevronLeft, ChevronRight } from "lucide-react";

export default function TransactionsPage() {
  const [transactions, setTxs]     = useState<Transaction[]>([]);
  const [total, setTotal]          = useState(0);
  const [page, setPage]            = useState(0);
  const [loading, setLoading]      = useState(true);
  const [search, setSearch]        = useState("");
  const [filter, setFilter]        = useState<string>("ALL");
  const limit = 20;

  const loadTxs = useCallback(async (p: number) => {
    setLoading(true);
    try {
      const res = await transactionsApi.getHistory(p, limit);
      setTxs(res.data?.transactions || []);
      setTotal(res.data?.total || 0);
    } catch {} finally { setLoading(false); }
  }, []);

  useEffect(() => { loadTxs(page); }, [page, loadTxs]);

  const filtered = transactions.filter(tx => {
    const matchSearch = !search || tx.description?.toLowerCase().includes(search.toLowerCase()) || tx.toAccount?.includes(search);
    const matchFilter = filter === "ALL" || tx.type === filter || tx.status === filter;
    return matchSearch && matchFilter;
  });

  return (
    <>
      <Header title="Movimientos" />
      <div className="flex-1 p-6 animate-fade-in">

        {/* Filters */}
        <div className="flex flex-col sm:flex-row gap-3 mb-6">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input className="input-neo pl-10" placeholder="Buscar movimientos…" value={search} onChange={e => setSearch(e.target.value)} />
          </div>
          <div className="flex gap-2">
            {[["ALL","Todos"],["COMPLETED","Completados"],["PENDING","Pendientes"],["FAILED","Fallidos"]].map(([v, l]) => (
              <button key={v} onClick={() => setFilter(v)}
                className={`px-4 py-2.5 rounded-xl text-sm font-medium transition-all ${filter === v ? "bg-blue-600 text-white" : "bg-[#13131f] border border-[#1e1e30] text-slate-400 hover:text-white hover:border-[#2a2a3d]"}`}>
                {l}
              </button>
            ))}
          </div>
        </div>

        <Card className="p-0 overflow-hidden">
          {loading ? (
            <div className="flex items-center justify-center py-16">
              <div className="w-7 h-7 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-slate-500">
              <Filter size={32} className="mb-3 opacity-40" />
              <p className="text-sm">Sin movimientos</p>
            </div>
          ) : (
            <>
              <div className="divide-y divide-[#1e1e30]">
                {filtered.map(tx => (
                  <div key={tx.id} className="flex items-center gap-4 px-5 py-4 hover:bg-white/2 transition-colors">
                    <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${tx.type === "DEPOSIT" ? "bg-emerald-500/10" : "bg-red-500/10"}`}>
                      {tx.type === "DEPOSIT" ? <ArrowDownLeft size={18} className="text-emerald-400" /> : <ArrowUpRight size={18} className="text-red-400" />}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-white truncate">{tx.description || tx.type}</p>
                      <p className="text-xs text-slate-500 mt-0.5 font-mono-neo">{tx.toAccount ? `→ ••••${tx.toAccount.slice(-4)}` : ""}</p>
                    </div>
                    <div className="hidden sm:block text-xs text-slate-500">{formatDate(tx.createdAt)}</div>
                    <div className="text-right flex flex-col items-end gap-1">
                      <p className={`text-sm font-semibold font-mono-neo ${tx.type === "DEPOSIT" ? "text-emerald-400" : "text-slate-200"}`}>
                        {tx.type === "DEPOSIT" ? "+" : "-"}{formatMXN(tx.amount)}
                      </p>
                      <Badge variant={tx.status === "COMPLETED" ? "success" : tx.status === "PENDING" ? "pending" : "failed"} className="text-[10px]">
                        {tx.status}
                      </Badge>
                    </div>
                  </div>
                ))}
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between px-5 py-4 border-t border-[#1e1e30]">
                <p className="text-sm text-slate-500">Total: {total} movimientos</p>
                <div className="flex gap-2">
                  <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}><ChevronLeft size={16} /></Button>
                  <span className="text-sm text-slate-400 flex items-center px-2">Pág. {page + 1}</span>
                  <Button variant="ghost" size="sm" disabled={(page + 1) * limit >= total} onClick={() => setPage(p => p + 1)}><ChevronRight size={16} /></Button>
                </div>
              </div>
            </>
          )}
        </Card>
      </div>
    </>
  );
}
