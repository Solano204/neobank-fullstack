"use client";
import { useState, Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import toast from "react-hot-toast";
import { Lock } from "lucide-react";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";

function ResetForm() {
  const router = useRouter();
  const params = useSearchParams();
  const email  = params.get("email") || "";
  const [form, setForm]       = useState({ code: "", newPassword: "", confirm: "" });
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (form.newPassword !== form.confirm) { toast.error("Las contraseñas no coinciden"); return; }
    setLoading(true);
    try {
      await authApi.resetPassword(email, form.code, form.newPassword);
      toast.success("¡Contraseña restablecida!");
      router.push("/auth/login");
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setLoading(false); }
  }

  return (
    <div className="animate-fade-in">
      <h1 className="font-display text-3xl font-bold text-white mb-2">Nueva contraseña</h1>
      <p className="text-slate-400 mb-8">Ingresa el código recibido y tu nueva contraseña.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Código de verificación" type="text" placeholder="123456" maxLength={6}
          value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} required />
        <Input label="Nueva contraseña" type="password" placeholder="Mínimo 8 caracteres"
          icon={<Lock size={16} />} value={form.newPassword} onChange={e => setForm({ ...form, newPassword: e.target.value })} required />
        <Input label="Confirmar contraseña" type="password" placeholder="Repite tu contraseña"
          icon={<Lock size={16} />} value={form.confirm} onChange={e => setForm({ ...form, confirm: e.target.value })} required />
        <Button type="submit" loading={loading} fullWidth>Restablecer contraseña</Button>
      </form>
      <p className="mt-6 text-center text-slate-400 text-sm">
        <Link href="/auth/login" className="text-blue-400 hover:text-blue-300">← Volver al inicio de sesión</Link>
      </p>
    </div>
  );
}

export default function ResetPasswordPage() {
  return <Suspense><ResetForm /></Suspense>;
}
