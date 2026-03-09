"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import Modal  from "@/components/ui/Modal";
import { usersApi }        from "@/lib/api/users";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";
import { useAuthStore }    from "@/lib/store/authStore";
import type { User, UserSettings } from "@/types";
import { User as UserIcon, Bell, Palette, Lock, LogOut, Save, Check } from "lucide-react";
import toast from "react-hot-toast";

export default function SettingsPage() {
  const { user, setUser, logout } = useAuthStore();
  const [settings, setSettings]   = useState<UserSettings | null>(null);
  const [profile,  setProfile]    = useState<Partial<User>>({});
  const [pwModal,  setPwModal]    = useState(false);
  const [pw,       setPw]         = useState({ current: "", new: "", confirm: "" });
  const [saving,   setSaving]     = useState(false);

  useEffect(() => {
    if (user) setProfile({ fullName: user.fullName, phone: user.phone, city: user.city, state: user.state });
    usersApi.getSettings().then(r => setSettings(r.data)).catch(() => {});
  }, [user]);

  async function saveProfile() {
    setSaving(true);
    try {
      const res = await usersApi.updateProfile(profile);
      setUser(res.data);
      toast.success("Perfil actualizado");
    } catch (err) { toast.error(getErrorMessage(err)); }
    finally { setSaving(false); }
  }

  async function saveSettings() {
    if (!settings) return;
    setSaving(true);
    try {
      await usersApi.updateSettings(settings);
      toast.success("Ajustes guardados");
    } catch (err) { toast.error(getErrorMessage(err)); }
    finally { setSaving(false); }
  }

  async function changePw() {
    if (pw.new !== pw.confirm) { toast.error("Las contraseñas no coinciden"); return; }
    try {
      await authApi.changePassword(pw.current, pw.new);
      toast.success("Contraseña actualizada");
      setPwModal(false);
      setPw({ current: "", new: "", confirm: "" });
    } catch (err) { toast.error(getErrorMessage(err)); }
  }

  function toggle(key: keyof UserSettings) {
    if (!settings) return;
    setSettings({ ...settings, [key]: !settings[key] });
  }

  const Toggle = ({ checked, onChange }: { checked: boolean; onChange: () => void }) => (
    <button onClick={onChange}
      className={`relative w-11 h-6 rounded-full transition-colors ${checked ? "bg-blue-600" : "bg-slate-700"}`}>
      <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${checked ? "translate-x-5" : ""}`} />
    </button>
  );

  return (
    <>
      <Header title="Ajustes" />
      <div className="flex-1 p-6 max-w-2xl mx-auto w-full animate-fade-in">
        <div className="flex flex-col gap-6">

          {/* Profile */}
          <Card>
            <div className="flex items-center gap-3 mb-5">
              <UserIcon size={18} className="text-blue-400" />
              <h3 className="font-display font-semibold text-white">Perfil</h3>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-5">
              <Input label="Nombre completo" value={profile.fullName || ""} onChange={e => setProfile({ ...profile, fullName: e.target.value })} />
              <Input label="Teléfono" type="tel" value={profile.phone || ""} onChange={e => setProfile({ ...profile, phone: e.target.value })} />
              <Input label="Ciudad"   value={profile.city  || ""} onChange={e => setProfile({ ...profile, city:  e.target.value })} />
              <Input label="Estado"   value={profile.state || ""} onChange={e => setProfile({ ...profile, state: e.target.value })} />
            </div>
            <div className="flex gap-3">
              <Button size="sm" onClick={saveProfile} loading={saving}><Save size={14} /> Guardar cambios</Button>
              <Button size="sm" variant="ghost" onClick={() => setPwModal(true)}><Lock size={14} /> Cambiar contraseña</Button>
            </div>
          </Card>

          {/* Notifications */}
          {settings && (
            <Card>
              <div className="flex items-center gap-3 mb-5">
                <Bell size={18} className="text-blue-400" />
                <h3 className="font-display font-semibold text-white">Notificaciones</h3>
              </div>
              <div className="flex flex-col gap-4">
                {([
                  ["emailNotifications", "Notificaciones por correo",   "Recibe alertas de transacciones y seguridad"],
                  ["pushNotifications",  "Notificaciones push",         "Alertas en tiempo real en tu dispositivo"],
                  ["smsNotifications",   "Notificaciones por SMS",      "Mensajes de texto para transacciones importantes"],
                ] as [keyof UserSettings, string, string][]).map(([k, l, d]) => (
                  <div key={k as string} className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-white">{l}</p>
                      <p className="text-xs text-slate-500 mt-0.5">{d}</p>
                    </div>
                    <Toggle checked={settings[k] as boolean} onChange={() => toggle(k)} />
                  </div>
                ))}
              </div>
              <Button size="sm" variant="ghost" className="mt-5" onClick={saveSettings} loading={saving}>
                <Check size={14} /> Guardar preferencias
              </Button>
            </Card>
          )}

          {/* Appearance */}
          {settings && (
            <Card>
              <div className="flex items-center gap-3 mb-5">
                <Palette size={18} className="text-blue-400" />
                <h3 className="font-display font-semibold text-white">Apariencia e idioma</h3>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-xs text-slate-500 mb-2">Idioma</p>
                  <select value={settings.language}
                    onChange={e => setSettings({ ...settings, language: e.target.value })}
                    className="input-neo text-sm">
                    <option value="es-MX">Español (México)</option>
                    <option value="en-US">English (US)</option>
                  </select>
                </div>
                <div>
                  <p className="text-xs text-slate-500 mb-2">Moneda</p>
                  <select value={settings.currency}
                    onChange={e => setSettings({ ...settings, currency: e.target.value })}
                    className="input-neo text-sm">
                    <option value="MXN">MXN — Peso mexicano</option>
                    <option value="USD">USD — Dólar americano</option>
                  </select>
                </div>
              </div>
            </Card>
          )}

          {/* Logout */}
          <Card className="border-red-500/10">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-white">Cerrar sesión</p>
                <p className="text-xs text-slate-500 mt-0.5">Se cerrará sesión en este dispositivo</p>
              </div>
              <Button variant="danger" size="sm" onClick={logout}><LogOut size={14} /> Salir</Button>
            </div>
          </Card>
        </div>
      </div>

      <Modal open={pwModal} onClose={() => setPwModal(false)} title="Cambiar contraseña">
        <div className="flex flex-col gap-4">
          <Input label="Contraseña actual"   type="password" value={pw.current} onChange={e => setPw({ ...pw, current: e.target.value })} />
          <Input label="Nueva contraseña"    type="password" value={pw.new}     onChange={e => setPw({ ...pw, new:     e.target.value })} />
          <Input label="Confirmar contraseña" type="password" value={pw.confirm} onChange={e => setPw({ ...pw, confirm: e.target.value })} />
          <Button onClick={changePw} fullWidth>Actualizar contraseña</Button>
        </div>
      </Modal>
    </>
  );
}
