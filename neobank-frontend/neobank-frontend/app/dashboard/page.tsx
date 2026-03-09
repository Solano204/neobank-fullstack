"use client";
import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Badge  from "@/components/ui/Badge";
import { useAuthStore }    from "@/lib/store/authStore";
import { accountsApi }     from "@/lib/api/accounts";
import { transactionsApi } from "@/lib/api/transactions";
import { notificationsApi }from "@/lib/api/notifications";
import { formatMXN, formatCLABE, maskAccount, formatDate } from "@/lib/utils";
import type { Account, Transaction, Notification } from "@/types";
import { Send, ArrowDownLeft, ArrowUpRight, CreditCard, Bell, BarChart3, ChevronRight, TrendingUp, Eye, EyeOff } from "lucide-react";

export default function DashboardPage() {
  const { user } = useAuthStore();
  const [accounts,      setAccounts]      = useState<Account[]>([]);
  const [transactions,  setTransactions]  = useState<Transaction[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [hideBalance,   setHideBalance]   = useState(false);

  const loadData = useCallback(async () => {
    try {
      const [accRes, txRes] = await Promise.allSettled([
        accountsApi.getAll(),
        transactionsApi.getHistory(0, 5),
      ]);
      if (accRes.status === "fulfilled") setAccounts(accRes.value.data?.accounts || []);
      if (txRes.status  === "fulfilled") setTransactions(txRes.value.data?.transactions || []);
      try {
        const notifRes = await notificationsApi.getAll();
        setNotifications((notifRes.data?.notifications || []).slice(0, 3));
      } catch { /* optional */ }
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const primaryAccount = accounts[0];
  const totalBalance   = accounts.reduce((s, a) => s + a.balance, 0);
  const unreadCount    = notifications.filter(n => !n.read).length;

  function txColor(tx: Transaction) {
    if (tx.status === "FAILED")    return "text-red-400";
    if (tx.status === "PENDING")   return "text-yellow-400";
    if (tx.type   === "DEPOSIT")   return "text-emerald-400";
    return "text-red-400";
  }
  function txSign(tx: Transaction) { return tx.type === "DEPOSIT" ? "+" : "-"; }

  if (loading) return (
    <>
      <Header title="Dashboard" />
      <div className="flex-1 p-6 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    </>
  );

  return (
    <>
      <Header title="Dashboard" />
      <div className="flex-1 p-6 space-y-6 animate-fade-in">

        {/* Balance Hero Card */}
        <div className="balance-card p-7">
          <div className="flex items-start justify-between mb-2">
            <div>
              <p className="text-slate-400 text-sm font-medium mb-1">Balance total</p>
              <div className="flex items-center gap-3">
                <h2 className="font-display text-4xl font-bold text-white">
                  {hideBalance ? "••••••" : formatMXN(totalBalance)}
                </h2>
                <button onClick={() => setHideBalance(!hideBalance)} className="text-slate-500 hover:text-slate-300 transition-colors mt-1">
                  {hideBalance ? <Eye size={18} /> : <EyeOff size={18} />}
                </button>
              </div>
              {primaryAccount && (
                <p className="text-slate-500 text-sm mt-2 font-mono-neo">{formatCLABE(primaryAccount.accountNumber)}</p>
              )}
            </div>
            <div className="flex items-center gap-1.5 bg-emerald-500/10 border border-emerald-500/20 rounded-full px-3 py-1.5">
              <TrendingUp size={14} className="text-emerald-400" />
              <span className="text-emerald-400 text-xs font-semibold">Activo</span>
            </div>
          </div>

          {/* Quick actions */}
          <div className="flex gap-3 mt-6">
            {[
              { href: "/transfer",     icon: Send,           label: "Transferir"  },
              { href: "/accounts",     icon: CreditCard,     label: "Cuentas"     },
              { href: "/transactions", icon: ArrowDownLeft,  label: "Movimientos" },
              { href: "/analytics",    icon: BarChart3,      label: "Análisis"    },
            ].map(({ href, icon: Icon, label }) => (
              <Link key={href} href={href} className="flex-1 flex flex-col items-center gap-2 py-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/8 transition-all text-slate-300 hover:text-white">
                <Icon size={18} />
                <span className="text-xs font-medium">{label}</span>
              </Link>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Recent Transactions */}
          <div className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-display font-semibold text-white">Movimientos recientes</h3>
              <Link href="/transactions" className="text-sm text-blue-400 hover:text-blue-300 flex items-center gap-1">
                Ver todos <ChevronRight size={14} />
              </Link>
            </div>
            <Card className="p-0 overflow-hidden">
              {transactions.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-slate-500">
                  <ArrowUpRight size={32} className="mb-3 opacity-40" />
                  <p className="text-sm">Sin movimientos aún</p>
                </div>
              ) : (
                <div className="divide-y divide-[#1e1e30]">
                  {transactions.map((tx) => (
                    <div key={tx.id} className="flex items-center gap-4 px-5 py-4 hover:bg-white/2 transition-colors">
                      <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${tx.type === "DEPOSIT" ? "bg-emerald-500/10" : "bg-red-500/10"}`}>
                        {tx.type === "DEPOSIT"
                          ? <ArrowDownLeft size={18} className="text-emerald-400" />
                          : <ArrowUpRight  size={18} className="text-red-400"     />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-white truncate">{tx.description || (tx.type === "DEPOSIT" ? "Depósito recibido" : "Transferencia enviada")}</p>
                        <p className="text-xs text-slate-500 mt-0.5">{formatDate(tx.createdAt)}</p>
                      </div>
                      <div className="text-right">
                        <p className={`text-sm font-semibold font-mono-neo ${txColor(tx)}`}>{txSign(tx)}{formatMXN(tx.amount)}</p>
                        <Badge variant={tx.status === "COMPLETED" ? "success" : tx.status === "PENDING" ? "pending" : "failed"} className="text-[10px] mt-1">
                          {tx.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>

          {/* Sidebar panel */}
          <div className="flex flex-col gap-4">
            {/* Accounts */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-display font-semibold text-white text-sm">Mis cuentas</h3>
                <Link href="/accounts" className="text-xs text-blue-400 hover:text-blue-300">Ver todas</Link>
              </div>
              {accounts.slice(0, 2).map(acc => (
                <Card key={acc.id} className="mb-2 p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <CreditCard size={14} className="text-blue-400" />
                      <span className="text-xs text-slate-400 font-medium">{acc.accountType}</span>
                    </div>
                    <Badge variant={acc.status === "ACTIVE" ? "success" : "failed"} className="text-[10px]">{acc.status}</Badge>
                  </div>
                  <p className="font-mono-neo text-sm text-slate-400 mb-1">{maskAccount(acc.accountNumber)}</p>
                  <p className="font-display text-xl font-bold text-white">{formatMXN(acc.balance)}</p>
                </Card>
              ))}
            </div>

            {/* Notifications */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-display font-semibold text-white text-sm flex items-center gap-2">
                  Notificaciones
                  {unreadCount > 0 && <span className="w-4 h-4 rounded-full bg-blue-500 text-white text-[10px] font-bold flex items-center justify-center">{unreadCount}</span>}
                </h3>
                <Link href="/notifications" className="text-xs text-blue-400 hover:text-blue-300">Ver todas</Link>
              </div>
              {notifications.length === 0 ? (
                <Card className="py-6 flex flex-col items-center text-slate-500 text-xs gap-2">
                  <Bell size={20} className="opacity-40" />
                  <span>Sin notificaciones</span>
                </Card>
              ) : (
                notifications.map(n => (
                  <Card key={n.id} className="mb-2 p-3">
                    <p className="text-sm font-medium text-white">{n.title}</p>
                    <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{n.message}</p>
                  </Card>
                ))
              )}
            </div>

            {/* KYC CTA */}
            {user?.kycStatus === "PENDING" && (
              <Card className="border-yellow-500/20 bg-yellow-500/5">
                <p className="text-sm font-semibold text-yellow-400 mb-1">Verifica tu identidad</p>
                <p className="text-xs text-slate-400 mb-3">Completa tu KYC para desbloquear límites más altos.</p>
                <Link href="/kyc" className="inline-flex items-center gap-1.5 text-xs font-semibold text-yellow-400 hover:text-yellow-300">
                  Verificar ahora <ChevronRight size={12} />
                </Link>
              </Card>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
