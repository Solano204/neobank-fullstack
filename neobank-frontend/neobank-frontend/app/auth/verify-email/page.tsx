"use client";
import { useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import toast from "react-hot-toast";
import { Mail } from "lucide-react";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";
import { useAuthStore }    from "@/lib/store/authStore";

function VerifyForm() {
  const router = useRouter();
  const params = useSearchParams();
  const email  = params.get("email") || "";
  const { setUser, setTokens } = useAuthStore();
  const [code, setCode]       = useState("");
  const [loading, setLoading] = useState(false);

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
    </div>
  );
}

export default function VerifyEmailPage() {
  return <Suspense><VerifyForm /></Suspense>;
}
