"use client";
import { useState, useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import toast from "react-hot-toast";
import { Mail } from "lucide-react";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";
import { useAuthStore }    from "@/lib/store/authStore";

const RESEND_COOLDOWN_SECONDS = 60;

function VerifyForm() {
  const router = useRouter();
  const params = useSearchParams();
  const email  = params.get("email") || "";
  const { setUser, setTokens } = useAuthStore();
  const [code, setCode]         = useState("");
  const [loading, setLoading]   = useState(false);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown]   = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown(s => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  async function handleResend() {
    setResending(true);
    try {
      await authApi.resendCode(email);
      toast.success("Código reenviado a tu correo");
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setResending(false); }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await authApi.verifyEmail(email, code);
      setTokens(res.data.accessToken, res.data.refreshToken);
      setUser(res.data.user);
      toast.success("¡Correo verificado!");
      router.push("/dashboard");
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setLoading(false); }
  }

  return (
    <div className="animate-fade-in">
      <div className="w-14 h-14 rounded-2xl bg-blue-600/15 border border-blue-500/25 flex items-center justify-center mb-6">
        <Mail size={24} className="text-blue-400" />
      </div>
      <h1 className="font-display text-3xl font-bold text-white mb-2">Verifica tu correo</h1>
      <p className="text-slate-400 mb-8">Ingresa el código de 6 dígitos enviado a{" "}
        <span className="text-white font-medium">{email}</span>
      </p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Código de verificación" type="text" placeholder="123456" maxLength={6}
          value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ""))} required />
        <Button type="submit" loading={loading} fullWidth>Verificar correo</Button>
      </form>
      <p className="text-sm text-slate-500 mt-5 text-center">
        ¿No recibiste el código?{" "}
        <button type="button" onClick={handleResend} disabled={resending || cooldown > 0}
          className="text-blue-400 hover:text-blue-300 font-medium disabled:text-slate-600 disabled:cursor-not-allowed">
          {cooldown > 0 ? `Reenviar en ${cooldown}s` : "Reenviar código"}
        </button>
      </p>
    </div>
  );
}

export default function VerifyEmailPage() {
  return <Suspense><VerifyForm /></Suspense>;
}
