"use client";
import { useEffect, useState } from "react";
import { Shield, Monitor, Smartphone, Trash2, AlertTriangle, CheckCircle2, Key, Eye } from "lucide-react";
import { security } from "@/lib/api";
import { formatDateTime, timeAgo, cn } from "@/lib/utils";
import type { Session, FraudAlert } from "@/lib/types";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

export default function SecurityPage() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [alerts, setAlerts] = useState<FraudAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [mfaLoading, setMfaLoading] = useState(false);
  const [mfaSuccess, setMfaSuccess] = useState("");

  useEffect(() => {
    Promise.allSettled([
      security.getSessions().then(r => setSessions(r.data ?? [])),
      security.getAlerts().then(r => setAlerts(r.data ?? [])),
    ]).finally(() => setLoading(false));
  }, []);

  const terminateSession = async (id: string) => {
    await security.terminateSession(id).catch(() => {});
    setSessions(prev => prev.filter(s => s.id !== id));
  };

  const terminateAll = async () => {
    if (!confirm("¿Cerrar todas las sesiones excepto esta?")) return;
    await security.terminateAllSessions().catch(() => {});
    setSessions(prev => prev.filter(s => s.current));
  };

  const enableMfa = async (method: "SMS" | "TOTP" | "EMAIL") => {
    setMfaLoading(true);
    try {
      await security.enableMfa(method);
      setMfaSuccess(`Código enviado por ${method}. Verifícalo para activar el 2FA.`);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Error al activar 2FA");
    } finally {
      setMfaLoading(false);
    }
  };

  const handleAlert = async (id: string, action: "confirm" | "report") => {
    if (action === "confirm") {
      await security.confirmAlert(id).catch(() => {});
    } else {
      const reason = prompt("Describe el problema:");
      if (!reason) return;
      await security.reportAlert(id, reason).catch(() => {});
    }
    setAlerts(prev => prev.filter(a => a.id !== id));
  };

  if (loading) {
    return <div className="space-y-4">{[1,2,3].map(i => <div key={i} className="skeleton h-32 rounded-2xl" />)}</div>;
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="font-display font-bold text-2xl text-slate-100">Seguridad</h1>
        <p className="text-slate-500 text-sm mt-1">Controla el acceso y la seguridad de tu cuenta</p>
      </div>

      {/* Fraud alerts */}
      {alerts.length > 0 && (
        <div className="space-y-3">
          <h2 className="font-display font-semibold text-slate-100 text-sm uppercase tracking-wide flex items-center gap-2">
            <AlertTriangle size={14} className="text-amber-400" /> Alertas de seguridad
          </h2>
          {alerts.map(a => (
            <div key={a.id} className={cn(
              "rounded-2xl border p-4 space-y-3",
              a.severity === "HIGH" ? "bg-rose-500/5 border-rose-500/20" :
              a.severity === "MEDIUM" ? "bg-amber-500/5 border-amber-500/20" :
              "bg-cyan-500/5 border-cyan-500/20"
            )}>
              <div className="flex items-start gap-3">
                <AlertTriangle size={18} className={
                  a.severity === "HIGH" ? "text-rose-400" :
                  a.severity === "MEDIUM" ? "text-amber-400" : "text-cyan-400"
                } />
                <div className="flex-1">
                  <p className="text-sm font-medium text-slate-100">{a.message}</p>
                  <p className="text-xs text-slate-500 mt-0.5">{timeAgo(a.createdAt)} • Severidad: {a.severity}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => handleAlert(a.id, "confirm")} className="flex-1">
                  <CheckCircle2 size={13} /> Fui yo
                </Button>
                <Button size="sm" variant="danger" onClick={() => handleAlert(a.id, "report")} className="flex-1">
                  <AlertTriangle size={13} /> Reportar fraude
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 2FA / MFA */}
      <Card>
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-cyan-500/10 flex items-center justify-center">
            <Key size={18} className="text-cyan-400" />
          </div>
          <div>
            <p className="font-semibold text-slate-100 text-sm">Autenticación de dos factores (2FA)</p>
            <p className="text-xs text-slate-500">Agrega una capa extra de seguridad a tu cuenta</p>
          </div>
        </div>
        {mfaSuccess && (
          <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-xl px-4 py-3 text-emerald-400 text-sm mb-4">
            {mfaSuccess}
          </div>
        )}
        <div className="grid grid-cols-3 gap-2">
          {(["SMS", "EMAIL", "TOTP"] as const).map(method => (
            <Button
              key={method}
              variant="secondary"
              size="sm"
              loading={mfaLoading}
              onClick={() => enableMfa(method)}
              className="text-xs"
            >
              {method === "TOTP" ? "App Auth" : method}
            </Button>
          ))}
        </div>
      </Card>

      {/* Active sessions */}
      <Card>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Monitor size={16} className="text-slate-400" />
            <p className="font-semibold text-slate-100 text-sm">Sesiones activas</p>
          </div>
          {sessions.filter(s => !s.current).length > 0 && (
            <Button size="sm" variant="danger" onClick={terminateAll}>Cerrar todas</Button>
          )}
        </div>
        {sessions.length === 0 ? (
          <p className="text-sm text-slate-500 py-4 text-center">No hay sesiones activas</p>
        ) : (
          <div className="space-y-2">
            {sessions.map(s => (
              <div key={s.id} className="flex items-center gap-3 p-3 rounded-xl bg-white/3 border border-white/5">
                <div className="w-9 h-9 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                  <Smartphone size={16} className="text-slate-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm text-slate-100 font-medium truncate">{s.device}</p>
                    {s.current && (
                      <span className="text-xs px-1.5 py-0.5 rounded-md bg-emerald-500/20 text-emerald-400 font-medium shrink-0">
                        Esta sesión
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-500">{s.ipAddress} • {timeAgo(s.lastActive)}</p>
                </div>
                {!s.current && (
                  <button
                    onClick={() => terminateSession(s.id)}
                    className="p-2 rounded-lg text-slate-600 hover:text-rose-400 hover:bg-rose-400/5 transition-all"
                  >
                    <Trash2 size={14} />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Security tips */}
      <Card>
        <div className="flex items-center gap-2 mb-3">
          <Shield size={16} className="text-cyan-400" />
          <p className="font-semibold text-slate-100 text-sm">Recomendaciones de seguridad</p>
        </div>
        <ul className="space-y-2 text-xs text-slate-400">
          {[
            "Activa la autenticación de dos factores (2FA)",
            "Nunca compartas tu NIP o contraseña con nadie",
            "NeoBank nunca te pedirá tu contraseña por teléfono",
            "Revisa tus movimientos regularmente",
            "Usa una contraseña única para tu cuenta NeoBank",
          ].map(tip => (
            <li key={tip} className="flex items-start gap-2">
              <CheckCircle2 size={12} className="text-emerald-400 mt-0.5 shrink-0" />
              {tip}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
