"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Mail } from "lucide-react";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail]     = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      await authApi.forgotPassword(email);
      toast.success("¡Código enviado a tu correo!");
      router.push(`/auth/reset-password?email=${encodeURIComponent(email)}`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setLoading(false); }
  }

  return (
    <div className="animate-fade-in">
      <h1 className="font-display text-3xl font-bold text-white mb-2">Recuperar contraseña</h1>
      <p className="text-slate-400 mb-8">Te enviaremos un código de verificación a tu correo.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <Input label="Correo electrónico" type="email" placeholder="carlos@ejemplo.com"
          icon={<Mail size={16} />} value={email} onChange={e => setEmail(e.target.value)} required />
        <Button type="submit" loading={loading} fullWidth>Enviar código</Button>
      </form>
      <p className="mt-6 text-center text-slate-400 text-sm">
        <Link href="/auth/login" className="text-blue-400 hover:text-blue-300">← Volver al inicio de sesión</Link>
      </p>
    </div>
  );
}
