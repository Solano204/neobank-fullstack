"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import Header  from "@/components/layout/Header";
import Card    from "@/components/ui/Card";
import Button  from "@/components/ui/Button";
import Input   from "@/components/ui/Input";
import Modal   from "@/components/ui/Modal";
import { accountsApi }     from "@/lib/api/accounts";
import { transactionsApi } from "@/lib/api/transactions";
import { contactsApi }     from "@/lib/api/contacts";
import { getErrorMessage } from "@/lib/api/client";
import { formatMXN, formatCLABE } from "@/lib/utils";
import type { Account, Contact } from "@/types";
import { Send, User, CheckCircle, AlertCircle, ChevronRight, Users } from "lucide-react";

type Step = "form" | "confirm" | "success";

export default function TransferPage() {
  const router = useRouter();
  const [step,          setStep]          = useState<Step>("form");
  const [accounts,      setAccounts]      = useState<Account[]>([]);
  const [contacts,      setContacts]      = useState<Contact[]>([]);
  const [loading,       setLoading]       = useState(false);
  const [validating,    setValidating]    = useState(false);
  const [recipientName, setRecipientName] = useState("");
  const [txResult,      setTxResult]      = useState<{ transactionId: string; newBalance: number } | null>(null);
  const [form, setForm] = useState({ from_account: "", to_account: "", amount: "", description: "" });

  useEffect(() => {
    Promise.all([accountsApi.getAll(), contactsApi.getAll()]).then(([a, c]) => {
      const accs = a.data?.accounts || [];
      setAccounts(accs);
      if (accs[0]) setForm(f => ({ ...f, from_account: accs[0].accountNumber }));
      setContacts(c.data?.contacts || []);
    }).catch(() => {});
  }, []);

  async function validateRecipient(account: string) {
    if (account.length !== 18) return;
    setValidating(true);
    try {
      const res = await transactionsApi.validateRecipient(account);
      if (res.data?.valid) { setRecipientName(res.data.name); toast.success(`Destinatario: ${res.data.name}`); }
      else { setRecipientName(""); toast.error("Cuenta no encontrada"); }
    } catch { setRecipientName(""); }
    finally { setValidating(false); }
  }

  async function handleTransfer() {
    setLoading(true);
    try {
      const res = await transactionsApi.transfer({
        from_account: form.from_account,
        to_account:   form.to_account,
        amount:       parseFloat(form.amount),
        currency:     "MXN",
        description:  form.description,
      });
      setTxResult({ transactionId: res.data?.transactionId || "", newBalance: res.data?.newBalance || 0 });
      setStep("success");
    } catch (err) {
      toast.error(getErrorMessage(err));
      setStep("form");
    } finally { setLoading(false); }
  }

  const fromAccount = accounts.find(a => a.accountNumber === form.from_account);

  return (
    <>
      <Header title="Transferir" />
      <div className="flex-1 p-6 max-w-2xl mx-auto w-full animate-fade-in">

        {/* ── STEP: FORM ──────────────────────────────── */}
        {step === "form" && (
          <div className="flex flex-col gap-6">
            <Card>
              <h3 className="font-display font-semibold text-white mb-4">Cuenta origen</h3>
              <div className="flex flex-col gap-2">
                {accounts.map(acc => (
                  <label key={acc.id}
                    className={`flex items-center gap-4 p-4 rounded-xl border cursor-pointer transition-all ${form.from_account === acc.accountNumber ? "border-blue-500/50 bg-blue-500/5" : "border-[#1e1e30] hover:border-[#2a2a3d]"}`}>
                    <input type="radio" name="from" value={acc.accountNumber}
                      checked={form.from_account === acc.accountNumber}
                      onChange={e => setForm({ ...form, from_account: e.target.value })}
                      className="hidden" />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-white">{acc.accountType}</p>
                      <p className="text-xs text-slate-500 font-mono-neo mt-0.5">{formatCLABE(acc.accountNumber)}</p>
                    </div>
                    <p className="font-display font-bold text-white">{formatMXN(acc.balance)}</p>
                  </label>
                ))}
              </div>
            </Card>

            <Card>
              <h3 className="font-display font-semibold text-white mb-4">Cuenta destino</h3>
              {contacts.length > 0 && (
                <div className="mb-4">
                  <p className="text-xs text-slate-500 font-medium mb-2 flex items-center gap-1.5">
                    <Users size={12} /> Contactos frecuentes
                  </p>
                  <div className="flex gap-2 flex-wrap">
                    {contacts.slice(0, 5).map(c => (
                      <button key={c.id}
                        onClick={() => { setForm({ ...form, to_account: c.accountNumber }); validateRecipient(c.accountNumber); }}
                        className="flex items-center gap-2 px-3 py-2 rounded-lg bg-[#1e1e30] hover:bg-[#2a2a3d] text-sm text-slate-300 hover:text-white transition-colors">
                        <User size={12} />
                        <span>{c.nickname || c.name || c.accountNumber.slice(-4)}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
              <Input label="CLABE / Número de cuenta (18 dígitos)" type="text" placeholder="000000000000000000"
                value={form.to_account}
                onChange={e => {
                  const v = e.target.value.replace(/\D/g, "").slice(0, 18);
                  setForm({ ...form, to_account: v });
                  if (v.length === 18) validateRecipient(v);
                }}
                suffix={
                  validating           ? <span className="text-xs text-blue-400 animate-pulse-soft">Validando…</span>
                  : recipientName      ? <CheckCircle size={16} className="text-emerald-400" />
                  : form.to_account.length === 18 ? <AlertCircle size={16} className="text-red-400" />
                  : undefined
                }
              />
              {recipientName && (
                <p className="text-sm text-emerald-400 mt-2 flex items-center gap-1.5">
                  <CheckCircle size={14} /> {recipientName}
                </p>
              )}
            </Card>

            <Card>
              <h3 className="font-display font-semibold text-white mb-4">Detalle</h3>
              <div className="flex flex-col gap-4">
                <Input label="Monto (MXN)" type="number" placeholder="0.00" min="1" max="50000"
                  value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })}
                  suffix={<span className="text-sm text-slate-400">MXN</span>} />
                <Input label="Descripción (opcional)" type="text" placeholder="Renta, nómina, regalo…"
                  value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
              </div>
            </Card>

            <Button
              disabled={!form.from_account || !form.to_account || !form.amount || parseFloat(form.amount) <= 0}
              onClick={() => setStep("confirm")} fullWidth>
              Continuar <ChevronRight size={18} />
            </Button>
          </div>
        )}

        {/* ── STEP: CONFIRM ───────────────────────────── */}
        {step === "confirm" && (
          <Card className="animate-fade-in">
            <div className="text-center mb-6">
              <div className="w-16 h-16 rounded-2xl bg-blue-600/15 border border-blue-500/25 flex items-center justify-center mx-auto mb-4">
                <Send size={28} className="text-blue-400" />
              </div>
              <h2 className="font-display text-2xl font-bold text-white mb-1">Confirmar transferencia</h2>
              <p className="text-slate-400 text-sm">Revisa los datos antes de confirmar</p>
            </div>
            <div className="bg-[#0f0f1a] rounded-xl p-5 flex flex-col gap-3 mb-6 border border-[#1e1e30]">
              {([
                ["De",              formatCLABE(form.from_account)],
                ["Para",            formatCLABE(form.to_account)],
                ["Destinatario",    recipientName || "—"],
                ["Monto",           formatMXN(parseFloat(form.amount))],
                ["Descripción",     form.description || "—"],
                ["Saldo después",   fromAccount ? formatMXN(fromAccount.balance - parseFloat(form.amount)) : "—"],
              ] as [string, string][]).map(([k, v]) => (
                <div key={k} className="flex items-center justify-between">
                  <span className="text-sm text-slate-500">{k}</span>
                  <span className="text-sm font-medium text-white font-mono-neo">{v}</span>
                </div>
              ))}
            </div>
            <div className="flex gap-3">
              <Button variant="ghost" onClick={() => setStep("form")} className="flex-1">Editar</Button>
              <Button onClick={handleTransfer} loading={loading} className="flex-1">Confirmar y enviar</Button>
            </div>
          </Card>
        )}

        {/* ── STEP: SUCCESS ───────────────────────────── */}
        {step === "success" && (
          <Card className="animate-fade-in text-center py-8">
            <div className="w-20 h-20 rounded-full bg-emerald-500/15 border border-emerald-500/25 flex items-center justify-center mx-auto mb-6">
              <CheckCircle size={40} className="text-emerald-400" />
            </div>
            <h2 className="font-display text-2xl font-bold text-white mb-2">¡Transferencia exitosa!</h2>
            <p className="text-slate-400 mb-1">
              Se enviaron <span className="text-white font-semibold">{formatMXN(parseFloat(form.amount))}</span>
            </p>
            <p className="text-xs text-slate-500 font-mono-neo mb-6">ID: {txResult?.transactionId}</p>
            {txResult?.newBalance !== undefined && (
              <p className="text-sm text-slate-400 mb-6">
                Nuevo saldo: <span className="text-white font-semibold">{formatMXN(txResult.newBalance)}</span>
              </p>
            )}
            <div className="flex gap-3 justify-center">
              <Button variant="ghost" onClick={() => { setStep("form"); setForm(f => ({ ...f, to_account: "", amount: "", description: "" })); setRecipientName(""); }}>
                Nueva transferencia
              </Button>
              <Button onClick={() => router.push("/dashboard")}>Ir al dashboard</Button>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}
