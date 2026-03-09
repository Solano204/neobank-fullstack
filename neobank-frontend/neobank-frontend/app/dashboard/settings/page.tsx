"use client";
import { useEffect, useState } from "react";
import { User, Lock, Bell, Eye, EyeOff, CheckCircle2 } from "lucide-react";
import { users, auth, accounts } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { validatePassword, formatDateTime } from "@/lib/utils";
import type { UserProfile, UserSettings } from "@/lib/types";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const [profile, setProfile] = useState<Partial<UserProfile>>({});
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState(false);
  const [pwForm, setPwForm] = useState({ currentPassword: "", newPassword: "", confirm: "" });
  const [showPw, setShowPw] = useState(false);
  const [savingPw, setSavingPw] = useState(false);
  const [pwError, setPwError] = useState("");
  const [pwSuccess, setPwSuccess] = useState(false);
  const [freezing, setFreezing] = useState(false);

  useEffect(() => {
    Promise.allSettled([
      users.getProfile().then(r => setProfile(r.data ?? {})),
      users.getSettings().then(r => setSettings(r.data ?? null)),
    ]).finally(() => setLoading(false));
  }, []);

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      await users.updateProfile(profile);
      refreshUser();
      setProfileSuccess(true);
      setTimeout(() => setProfileSuccess(false), 3000);
    } catch { } finally {
      setSavingProfile(false);
    }
  };

  const savePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwError("");
    if (pwForm.newPassword !== pwForm.confirm) { setPwError("Las contraseñas no coinciden"); return; }
    const { valid, message } = validatePassword(pwForm.newPassword);
    if (!valid) { setPwError(message); return; }
    setSavingPw(true);
    try {
      await auth.changePassword({ currentPassword: pwForm.currentPassword, newPassword: pwForm.newPassword });
      setPwForm({ currentPassword: "", newPassword: "", confirm: "" });
      setPwSuccess(true);
      setTimeout(() => setPwSuccess(false), 3000);
    } catch (err) {
      setPwError(err instanceof Error ? err.message : "Error al cambiar contraseña");
    } finally {
      setSavingPw(false);
    }
  };

  const freezeAccount = async () => {
    const reason = prompt("¿Por qué deseas congelar tu cuenta?");
    if (!reason) return;
    setFreezing(true);
    try {
      const accsRes = await accounts.getAll();
      const acc = accsRes.data?.[0];
      if (acc) await accounts.freeze(acc.id, reason);
      alert("Cuenta congelada. Contacta a soporte para descongelarla.");
    } catch { } finally {
      setFreezing(false);
    }
  };

  const saveSettings = async () => {
    if (!settings) return;
    setSavingSettings(true);
    try {
      await users.updateSettings(settings);
    } catch { } finally {
      setSavingSettings(false);
    }
  };

  if (loading) {
    return <div className="space-y-4">{[1,2,3].map(i => <div key={i} className="skeleton h-40 rounded-2xl" />)}</div>;
  }

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <h1 className="font-display font-bold text-2xl text-slate-100">Ajustes</h1>

      {/* Profile */}
      <Card>
        <div className="flex items-center gap-2 mb-5">
          <User size={16} className="text-slate-400" />
          <h2 className="font-display font-semibold text-slate-100 text-sm">Información personal</h2>
        </div>
        <form onSubmit={saveProfile} className="space-y-4">
          <Input
            label="Nombre completo"
            value={profile.fullName ?? ""}
            onChange={e => setProfile(p => ({ ...p, fullName: e.target.value }))}
          />
          <Input
            label="Correo electrónico"
            type="email"
            value={profile.email ?? ""}
            disabled
            className="opacity-50 cursor-not-allowed"
          />
          <Input
            label="Teléfono"
            type="tel"
            value={profile.phone ?? ""}
            onChange={e => setProfile(p => ({ ...p, phone: e.target.value }))}
          />
          <Input
            label="Fecha de nacimiento"
            type="date"
            value={profile.dateOfBirth ?? ""}
            onChange={e => setProfile(p => ({ ...p, dateOfBirth: e.target.value }))}
          />
          {profileSuccess && (
            <div className="flex items-center gap-2 text-emerald-400 text-sm">
              <CheckCircle2 size={14} /> Perfil actualizado
            </div>
          )}
          <Button type="submit" loading={savingProfile} className="w-full">
            Guardar cambios
          </Button>
        </form>
      </Card>

      {/* Password */}
      <Card>
        <div className="flex items-center gap-2 mb-5">
          <Lock size={16} className="text-slate-400" />
          <h2 className="font-display font-semibold text-slate-100 text-sm">Cambiar contraseña</h2>
        </div>
        <form onSubmit={savePassword} className="space-y-4">
          <Input
            label="Contraseña actual"
            type={showPw ? "text" : "password"}
            value={pwForm.currentPassword}
            onChange={e => setPwForm(f => ({ ...f, currentPassword: e.target.value }))}
            suffix={
              <button type="button" onClick={() => setShowPw(v => !v)} className="text-slate-500 hover:text-slate-300">
                {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            }
          />
          <Input
            label="Nueva contraseña"
            type="password"
            value={pwForm.newPassword}
            onChange={e => setPwForm(f => ({ ...f, newPassword: e.target.value }))}
          />
          <Input
            label="Confirmar nueva contraseña"
            type="password"
            value={pwForm.confirm}
            onChange={e => setPwForm(f => ({ ...f, confirm: e.target.value }))}
            error={pwError}
          />
          {pwSuccess && (
            <div className="flex items-center gap-2 text-emerald-400 text-sm">
              <CheckCircle2 size={14} /> Contraseña actualizada
            </div>
          )}
          <Button type="submit" loading={savingPw} className="w-full">
            Cambiar contraseña
          </Button>
        </form>
      </Card>

      {/* Notifications */}
      {settings && (
        <Card>
          <div className="flex items-center gap-2 mb-5">
            <Bell size={16} className="text-slate-400" />
            <h2 className="font-display font-semibold text-slate-100 text-sm">Notificaciones</h2>
          </div>
          <div className="space-y-4">
            {([
              ["push", "Notificaciones push"],
              ["email", "Notificaciones por correo"],
              ["sms", "Notificaciones por SMS"],
            ] as [keyof UserSettings["notifications"], string][]).map(([key, label]) => (
              <div key={key} className="flex items-center justify-between">
                <span className="text-sm text-slate-300">{label}</span>
                <button
                  onClick={() => {
                    setSettings(s => s ? { ...s, notifications: { ...s.notifications, [key]: !s.notifications[key] } } : s);
                    saveSettings();
                  }}
                  className={`relative w-11 h-6 rounded-full transition-colors ${
                    settings.notifications[key] ? "bg-cyan-500" : "bg-navy-600"
                  }`}
                >
                  <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                    settings.notifications[key] ? "translate-x-5" : ""
                  }`} />
                </button>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Danger zone */}
      <Card>
        <h2 className="font-display font-semibold text-rose-400 text-sm mb-3">Zona de riesgo</h2>
        <p className="text-xs text-slate-500 mb-4">
          Congelar tu cuenta bloqueará todas las transacciones. Podrás desactivarlo contactando a soporte.
        </p>
        <Button variant="danger" loading={freezing} onClick={freezeAccount} className="w-full">
          Congelar cuenta
        </Button>
      </Card>
    </div>
  );
}
