"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Badge  from "@/components/ui/Badge";
import Modal  from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { accountsApi }     from "@/lib/api/accounts";
import { getErrorMessage } from "@/lib/api/client";
import { formatMXN, formatCLABE, formatDate } from "@/lib/utils";
import type { Account } from "@/types";
import { CreditCard, Copy, Snowflake, Unlock } from "lucide-react";
import toast from "react-hot-toast";

export default function AccountsPage() {
  const [accounts,      setAccounts]      = useState<Account[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [freezeModal,   setFreezeModal]   = useState<Account | null>(null);
  const [freezeReason,  setFreezeReason]  = useState("");
  const [freezeLoading, setFreezeLoading] = useState(false);

  useEffect(() => {
    accountsApi.getAll()
      .then(r => setAccounts(r.data?.accounts || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  async function handleFreeze(acc: Account) {
    setFreezeLoading(true);
    try {
      await accountsApi.freeze(acc.id, freezeReason);
      toast.success("Cuenta congelada");
      setAccounts(a => a.map(x => x.id === acc.id ? { ...x, status: "FROZEN" as const } : x));
      setFreezeModal(null);
      setFreezeReason("");
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setFreezeLoading(false); }
  }

  return (
    <>
      <Header title="Cuentas" />
      <div className="flex-1 p-6 animate-fade-in">
        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="w-7 h-7 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {accounts.map(acc => (
              <Card key={acc.id} className="flex flex-col gap-5">
                <div className="balance-card p-6 -mx-5 -mt-5 rounded-t-xl">
                  <div className="flex items-start justify-between mb-6">
                    <div className="flex items-center gap-2">
                      <CreditCard size={18} className="text-blue-400" />
                      <span className="text-sm font-medium text-slate-300">{acc.accountType}</span>
                    </div>
                    <Badge variant={acc.status === "ACTIVE" ? "success" : acc.status === "FROZEN" ? "info" : "failed"}>
                      {acc.status}
                    </Badge>
                  </div>
                  <p className="font-mono-neo text-slate-400 text-sm mb-2">{formatCLABE(acc.accountNumber)}</p>
                  <p className="font-display text-3xl font-bold text-white">{formatMXN(acc.balance)}</p>
                  <p className="text-xs text-slate-500 mt-1">Disponible: {formatMXN(acc.availableBalance)}</p>
                </div>

                <div className="grid grid-cols-2 gap-3 text-sm">
                  {([
                    ["Moneda",               acc.currency],
                    ["Último movimiento",    acc.lastTransactionAt ? formatDate(acc.lastTransactionAt) : "—"],
                    ["Límite descubierto",   formatMXN(acc.overdraftLimit || 0)],
                    ["Tasa interés",         acc.interestRate ? `${acc.interestRate}%` : "—"],
                  ] as [string, string][]).map(([k, v]) => (
                    <div key={k}>
                      <p className="text-xs text-slate-500">{k}</p>
                      <p className="text-white font-medium mt-0.5 truncate">{v}</p>
                    </div>
                  ))}
                </div>

                <div className="flex gap-2">
                  <Button size="sm" variant="ghost" className="flex-1"
                    onClick={() => { navigator.clipboard.writeText(acc.accountNumber); toast.success("CLABE copiada"); }}>
                    <Copy size={14} /> Copiar CLABE
                  </Button>
                  {acc.status === "ACTIVE" ? (
                    <Button size="sm" variant="danger" className="flex-1" onClick={() => setFreezeModal(acc)}>
                      <Snowflake size={14} /> Congelar
                    </Button>
                  ) : acc.status === "FROZEN" ? (
                    <Button size="sm" variant="success" className="flex-1"
                      onClick={() => toast("Contacta soporte para descongelar")}>
                      <Unlock size={14} /> Descongelar
                    </Button>
                  ) : null}
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      <Modal open={!!freezeModal} onClose={() => setFreezeModal(null)} title="Congelar cuenta">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-slate-400">
            Esta acción bloqueará todas las transacciones en tu cuenta{" "}
            <span className="text-white font-mono-neo">••••{freezeModal?.accountNumber.slice(-4)}</span>.
          </p>
          <Input label="Motivo (opcional)" placeholder="Tarjeta perdida, actividad sospechosa…"
            value={freezeReason} onChange={e => setFreezeReason(e.target.value)} />
          <div className="flex gap-3">
            <Button variant="ghost" onClick={() => setFreezeModal(null)} className="flex-1">Cancelar</Button>
            <Button variant="danger" loading={freezeLoading}
              onClick={() => freezeModal && handleFreeze(freezeModal)} className="flex-1">
              <Snowflake size={16} /> Congelar cuenta
            </Button>
          </div>
        </div>
      </Modal>
    </>
  );
}
