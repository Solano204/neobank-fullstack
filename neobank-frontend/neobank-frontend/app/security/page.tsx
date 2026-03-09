"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Badge  from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal  from "@/components/ui/Modal";
import Input  from "@/components/ui/Input";
import { securityApi }     from "@/lib/api/security";
import { getErrorMessage } from "@/lib/api/client";
import type { UserSession, FraudAlert } from "@/types";
import { Shield, Monitor, AlertTriangle, X, CheckCircle, Flag, Lock } from "lucide-react";
import { formatDate } from "@/lib/utils";
import toast from "react-hot-toast";

export default function SecurityPage() {
  const [sessions,     setSessions]     = useState<UserSession[]>([]);
  const [alerts,       setAlerts]       = useState<FraudAlert[]>([]);
  const [loading,      setLoading]      = useState(true);
  const [mfaModal,     setMfaModal]     = useState(false);
  const [mfaCode,      setMfaCode]      = useState("");
  const [reportModal,  setReportModal]  = useState<FraudAlert | null>(null);
  const [reportReason, setReportReason] = useState("");

  useEffect(() => {
    Promise.allSettled([securityApi.getSessions(), securityApi.getAlerts()]).then(([s, a]) => {
      if (s.status === "fulfilled") setSessions(s.value.data?.sessions || []);
      if (a.status === "fulfilled") setAlerts(a.value.data?.alerts   || []);
    }).finally(() => setLoading(false));
  }, []);

  async function terminateSession(id: string) {
    try {
      await securityApi.deleteSession(id);
      setSessions(s => s.filter(x => x.id !== id));
      toast.success("Sesión cerrada");
    } catch (err) { toast.error(getErrorMessage(err)); }
  }

  async function enableMfa() {
    try {
      await securityApi.enableMfa("SMS");
      toast.success("Código enviado a tu teléfono");
    } catch (err) { toast.error(getErrorMessage(err)); }
  }

  async function verifyMfa() {
    try {
      await securityApi.verifyMfa(mfaCode);
      toast.success("MFA activado exitosamente");
      setMfaModal(false);
    } catch (err) { toast.error(getErrorMessage(err)); }
  }

  async function handleReport() {
    if (!reportModal) return;
    try {
      await securityApi.reportAlert(reportModal.id, reportReason);
      toast.success("Transacción reportada. Tu cuenta está protegida.");
      setAlerts(a => a.filter(x => x.id !== reportModal.id));
      setReportModal(null);
    } catch (err) { toast.error(getErrorMessage(err)); }
  }

  const severityBadge: Record<string, "failed" | "pending" | "info"> = { HIGH: "failed", MEDIUM: "pending", LOW: "info" };

  return (
    <>
      <Header title="Seguridad" />
      <div className="flex-1 p-6 max-w-3xl mx-auto w-full animate-fade-in">

        {/* MFA */}
        <Card className="mb-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-blue-600/15 border border-blue-500/25 flex items-center justify-center">
                <Lock size={22} className="text-blue-400" />
              </div>
              <div>
                <p className="font-semibold text-white">Autenticación en dos pasos (MFA)</p>
                <p className="text-xs text-slate-400 mt-0.5">Agrega una capa extra de seguridad a tu cuenta</p>
              </div>
            </div>
            <Button size="sm" onClick={() => { setMfaModal(true); enableMfa(); }}>Activar MFA</Button>
          </div>
        </Card>

        {/* Fraud Alerts */}
        {alerts.length > 0 && (
          <div className="mb-6">
            <h3 className="font-display font-semibold text-white mb-3 flex items-center gap-2">
              <AlertTriangle size={18} className="text-yellow-400" /> Alertas de seguridad
            </h3>
            <div className="flex flex-col gap-3">
              {alerts.map(alert => (
                <Card key={alert.id} className="border-yellow-500/20">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <Badge variant={severityBadge[alert.severity]}>{alert.severity}</Badge>
                        <span className="text-xs text-slate-500">{formatDate(alert.createdAt)}</span>
                      </div>
                      <p className="text-sm text-white">{alert.message}</p>
                    </div>
                    <div className="flex gap-2 flex-shrink-0">
                      <Button size="sm" variant="success"
                        onClick={async () => {
                          await securityApi.confirmAlert(alert.id);
                          setAlerts(a => a.filter(x => x.id !== alert.id));
                          toast.success("Confirmado como legítimo");
                        }}>
                        <CheckCircle size={14} /> Fui yo
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => setReportModal(alert)}>
                        <Flag size={14} /> Reportar
                      </Button>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          </div>
        )}

        {/* Sessions */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-display font-semibold text-white flex items-center gap-2">
              <Monitor size={18} /> Sesiones activas
            </h3>
            <Button size="sm" variant="danger"
              onClick={async () => { await securityApi.deleteAllSessions(); setSessions([]); toast.success("Todas las sesiones cerradas"); }}>
              Cerrar todas
            </Button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-24">
              <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : sessions.length === 0 ? (
            <Card className="text-center py-8 text-slate-500 text-sm">
              <Shield size={28} className="mx-auto mb-2 opacity-30" /> Sin sesiones activas
            </Card>
          ) : (
            <div className="flex flex-col gap-3">
              {sessions.map(s => (
                <Card key={s.id} className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-800 flex items-center justify-center flex-shrink-0">
                    <Monitor size={18} className="text-slate-400" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-white">{s.device || "Dispositivo desconocido"}</p>
                    <p className="text-xs text-slate-500">{s.location} · {s.ipAddress}</p>
                    <p className="text-xs text-slate-600 mt-0.5">Activo: {formatDate(s.lastActive)}</p>
                  </div>
                  <button onClick={() => terminateSession(s.id)}
                    className="text-slate-500 hover:text-red-400 transition-colors p-2">
                    <X size={18} />
                  </button>
                </Card>
              ))}
            </div>
          )}
        </div>
      </div>

      <Modal open={mfaModal} onClose={() => setMfaModal(false)} title="Activar MFA">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-slate-400">Hemos enviado un código de verificación a tu teléfono registrado.</p>
          <Input label="Código de verificación" type="text" placeholder="123456" maxLength={6}
            value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g, ""))} />
          <Button onClick={verifyMfa} disabled={mfaCode.length < 6} fullWidth>Activar MFA</Button>
        </div>
      </Modal>

      <Modal open={!!reportModal} onClose={() => setReportModal(null)} title="Reportar transacción">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-slate-400">Explica por qué no autorizaste esta transacción.</p>
          <Input label="Razón" placeholder="No reconozco este cargo…"
            value={reportReason} onChange={e => setReportReason(e.target.value)} />
          <Button variant="danger" onClick={handleReport} fullWidth>Reportar y congelar cuenta</Button>
        </div>
      </Modal>
    </>
  );
}
