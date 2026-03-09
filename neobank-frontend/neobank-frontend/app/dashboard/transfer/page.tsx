"use client";
import { useEffect, useState, useCallback } from "react";
import { CheckCircle2, XCircle, ArrowRightLeft, ChevronRight, User } from "lucide-react";
import { accounts, transactions, contacts } from "@/lib/api";
import { formatCurrency, isValidClabe, cn } from "@/lib/utils";
import type { Account, Contact } from "@/lib/types";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";

type Step = "form" | "confirm" | "success";

export default function TransferPage() {
  const [step, setStep] = useState<Step>("form");
  const [myAccounts, setMyAccounts] = useState<Account[]>([]);
  const [myContacts, setMyContacts] = useState<Contact[]>([]);
  const [form, setForm] = useState({
    fromAccount: "",
    toAccount: "",
    amount: "",
    description: "",
  });
  const [recipient, setRecipient] = useState<{ name: string; bank: string } | null>(null);
  const [validating, setValidating] = useState(false);
  const [validationError, setValidationError] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<{ transactionId: string; newBalance: number } | null>(null);

  useEffect(() => {
    accounts.getAll().then(r => {
      const accs = r.data ?? [];
      setMyAccounts(accs);
      if (accs.length) setForm(f => ({ ...f, fromAccount: accs[0].accountNumber }));
    }).catch(() => {});
    contacts.getAll().then(r => setMyContacts(r.data ?? [])).catch(() => {});
  }, []);

  // Validate recipient when CLABE is 18 digits
  const validateRecipient = useCallback(async (clabe: string) => {
    if (!isValidClabe(clabe)) { setRecipient(null); return; }
    setValidating(true);
    setValidationError("");
    try {
      const res = await transactions.validateRecipient({ accountNumber: clabe });
      if (res.valid) {
        setRecipient({ name: res.recipientName ?? "Cuenta válida", bank: res.bank ?? "NeoBank" });
      } else {
        setRecipient(null);
        setValidationError("Cuenta no encontrada");
      }
    } catch {
      setRecipient(null);
      setValidationError("No se pudo validar la cuenta");
    } finally {
      setValidating(false);
    }
  }, []);

  const handleClabeChange = (val: string) => {
    const clean = val.replace(/\D/g, "").slice(0, 18);
    setForm(f => ({ ...f, toAccount: clean }));
    setRecipient(null);
    setValidationError("");
    if (clean.length === 18) validateRecipient(clean);
  };

  const currentAccount = myAccounts.find(a => a.accountNumber === form.fromAccount);

  const handleConfirm = async () => {
    setError("");
    setLoading(true);
    try {
      const res = await transactions.transfer({
        fromAccount: form.fromAccount,
        toAccount: form.toAccount,
        amount: parseFloat(form.amount),
        description: form.description,
      });
      setResult({ transactionId: res.transactionId, newBalance: res.newBalance });
      setStep("success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al realizar transferencia");
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setStep("form");
    setForm(f => ({ ...f, toAccount: "", amount: "", description: "" }));
    setRecipient(null);
    setResult(null);
    setError("");
  };

  if (step === "success" && result) {
    return (
      <div className="max-w-md mx-auto text-center animate-fade-up">
        <div className="w-20 h-20 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center mx-auto mb-6">
          <CheckCircle2 size={36} className="text-emerald-400" />
        </div>
        <h2 className="font-display font-bold text-2xl text-slate-100 mb-2">¡Transferencia exitosa!</h2>
        <p className="text-slate-400 text-sm mb-6">
          Tu transferencia de <span className="text-slate-200 font-semibold">{formatCurrency(parseFloat(form.amount))}</span> fue procesada correctamente.
        </p>
        <Card className="text-left space-y-3 mb-6">
          {[
            ["ID de transacción", result.transactionId.slice(0, 16) + "..."],
            ["Para", recipient?.name ?? form.toAccount],
            ["Monto", formatCurrency(parseFloat(form.amount))],
            ["Nuevo saldo", formatCurrency(result.newBalance)],
          ].map(([label, val]) => (
            <div key={label} className="flex justify-between text-sm">
              <span className="text-slate-500">{label}</span>
              <span className="text-slate-200 font-medium font-mono">{val}</span>
            </div>
          ))}
        </Card>
        <Button onClick={reset} size="lg" className="w-full">Nueva transferencia</Button>
      </div>
    );
  }

  if (step === "confirm") {
    return (
      <div className="max-w-md mx-auto animate-fade-up">
        <h1 className="font-display font-bold text-2xl text-slate-100 mb-6">Confirmar transferencia</h1>
        <Card className="space-y-4 mb-4">
          {[
            ["Desde", currentAccount ? `•••• ${form.fromAccount.slice(-4)}` : form.fromAccount],
            ["Para", recipient?.name ?? form.toAccount],
            ["CLABE", form.toAccount],
            ["Banco", recipient?.bank ?? "—"],
            ["Monto", formatCurrency(parseFloat(form.amount))],
            ["Descripción", form.description || "—"],
          ].map(([label, val]) => (
            <div key={label} className="flex justify-between text-sm py-2 border-b border-white/5 last:border-0">
              <span className="text-slate-500">{label}</span>
              <span className="text-slate-100 font-medium">{val}</span>
            </div>
          ))}
        </Card>
        {error && (
          <div className="bg-rose-500/10 border border-rose-500/30 rounded-lg px-4 py-3 text-rose-400 text-sm mb-4">
            {error}
          </div>
        )}
        <div className="flex gap-3">
          <Button variant="secondary" size="lg" className="flex-1" onClick={() => setStep("form")}>
            Regresar
          </Button>
          <Button size="lg" loading={loading} className="flex-1" onClick={handleConfirm}>
            Confirmar
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto">
      <h1 className="font-display font-bold text-2xl text-slate-100 mb-6">Transferir dinero</h1>

      <div className="space-y-5">
        {/* From account */}
        <Card>
          <p className="text-xs text-slate-500 font-medium uppercase tracking-wide mb-3">Cuenta origen</p>
          {myAccounts.length === 0 ? (
            <div className="text-sm text-slate-500 py-2">Cargando cuentas...</div>
          ) : (
            <div className="space-y-2">
              {myAccounts.map(acc => (
                <button
                  key={acc.id}
                  onClick={() => setForm(f => ({ ...f, fromAccount: acc.accountNumber }))}
                  className={cn(
                    "w-full flex items-center gap-3 p-3 rounded-xl border transition-all",
                    form.fromAccount === acc.accountNumber
                      ? "border-cyan-500/40 bg-cyan-500/10"
                      : "border-white/8 bg-white/3 hover:border-white/15"
                  )}
                >
                  <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-500 to-emerald-500 flex items-center justify-center shrink-0">
                    <ArrowRightLeft size={16} className="text-navy-950" />
                  </div>
                  <div className="flex-1 text-left">
                    <p className="text-sm text-slate-100 font-medium">•••• {acc.accountNumber.slice(-4)}</p>
                    <p className="text-xs text-slate-500">{formatCurrency(acc.balance)} disponible</p>
                  </div>
                  {form.fromAccount === acc.accountNumber && (
                    <CheckCircle2 size={16} className="text-cyan-400 shrink-0" />
                  )}
                </button>
              ))}
            </div>
          )}
        </Card>

        {/* Destination */}
        <Card>
          <p className="text-xs text-slate-500 font-medium uppercase tracking-wide mb-3">Cuenta destino</p>

          {/* Favorite contacts */}
          {myContacts.filter(c => c.favorite).length > 0 && (
            <div className="mb-3">
              <p className="text-xs text-slate-600 mb-2">Favoritos</p>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {myContacts.filter(c => c.favorite).map(c => (
                  <button
                    key={c.id}
                    onClick={() => { setForm(f => ({ ...f, toAccount: c.accountNumber })); validateRecipient(c.accountNumber); }}
                    className="flex flex-col items-center gap-1 min-w-14"
                  >
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 border border-white/10 flex items-center justify-center">
                      <User size={16} className="text-slate-300" />
                    </div>
                    <span className="text-xs text-slate-400 truncate w-14 text-center">{c.nickname ?? c.name.split(" ")[0]}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <Input
            label="CLABE (18 dígitos)"
            type="text"
            inputMode="numeric"
            placeholder="012345678901234567"
            value={form.toAccount}
            onChange={e => handleClabeChange(e.target.value)}
            error={validationError}
          />

          {validating && (
            <div className="flex items-center gap-2 mt-2 text-xs text-slate-500">
              <div className="w-3 h-3 border border-cyan-500/50 border-t-cyan-500 rounded-full animate-spin" />
              Verificando cuenta...
            </div>
          )}
          {recipient && (
            <div className="flex items-center gap-2 mt-2 px-3 py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
              <CheckCircle2 size={14} className="text-emerald-400 shrink-0" />
              <div>
                <p className="text-sm text-emerald-300 font-medium">{recipient.name}</p>
                <p className="text-xs text-emerald-400/60">{recipient.bank}</p>
              </div>
            </div>
          )}
        </Card>

        {/* Amount */}
        <Card>
          <p className="text-xs text-slate-500 font-medium uppercase tracking-wide mb-3">Monto</p>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 font-mono text-sm">$</span>
            <input
              type="number"
              placeholder="0.00"
              value={form.amount}
              onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
              className="w-full h-14 bg-navy-800/80 border border-white/10 rounded-xl text-slate-100 pl-8 pr-16 text-2xl font-mono font-semibold focus:border-cyan-500/60 transition-all"
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 text-sm">MXN</span>
          </div>
          {currentAccount && form.amount && parseFloat(form.amount) > currentAccount.balance && (
            <p className="text-xs text-rose-400 mt-2 flex items-center gap-1.5">
              <XCircle size={12} /> Saldo insuficiente
            </p>
          )}

          <Input
            label="Descripción (opcional)"
            type="text"
            placeholder="Renta, deuda, regalo..."
            value={form.description}
            onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
            className="mt-4"
          />
        </Card>

        <Button
          size="lg"
          className="w-full"
          disabled={
            !form.fromAccount ||
            !form.toAccount ||
            !form.amount ||
            parseFloat(form.amount) <= 0 ||
            !recipient ||
            (currentAccount ? parseFloat(form.amount) > currentAccount.balance : false)
          }
          onClick={() => setStep("confirm")}
        >
          Continuar <ChevronRight size={16} />
        </Button>
      </div>
    </div>
  );
}
