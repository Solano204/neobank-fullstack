"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Mail, Lock } from "lucide-react";
import Button  from "@/components/ui/Button";
import Input   from "@/components/ui/Input";
import { authApi }           from "@/lib/api/auth";
import { getErrorMessage }   from "@/lib/api/client";
import { useAuthStore }      from "@/lib/store/authStore";

export default function LoginPage() {
  const router = useRouter();
  const { setUser, setTokens } = useAuthStore();
  const [loading, setLoading]  = useState(false);
  const [form, setForm]        = useState({ email: "", password: "" });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await authApi.login(form);
      setTokens(res.data.accessToken, res.data.refreshToken);
      setUser(res.data.user);
      toast.success("¡Bienvenido de vuelta!");
      router.push("/dashboard");
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="animate-fade-in">
      <div className="mb-8">
        <h1 className="font-display text-3xl font-bold text-white mb-2">Iniciar sesión</h1>
        <p className="text-slate-400">¿No tienes cuenta?{" "}
          <Link href="/auth/signup" className="text-blue-400 hover:text-blue-300 font-medium">Regístrate gratis</Link>
        </p>
      </div>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <Input label="Correo electrónico" type="email" placeholder="carlos@ejemplo.com"
          icon={<Mail size={16} />}
          value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} required />
        <Input label="Contraseña" type="password" placeholder="••••••••"
          icon={<Lock size={16} />}
          value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} required />
        <div className="flex justify-end -mt-2">
          <Link href="/auth/forgot-password" className="text-sm text-slate-400 hover:text-blue-400 transition-colors">
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        <Button type="submit" loading={loading} fullWidth>Iniciar sesión</Button>
      </form>
    </div>
  );
}
