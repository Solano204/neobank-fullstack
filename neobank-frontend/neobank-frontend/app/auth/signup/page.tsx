"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Mail, Lock, User, Phone } from "lucide-react";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import { authApi }         from "@/lib/api/auth";
import { getErrorMessage } from "@/lib/api/client";

export default function SignupPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [form, setForm]       = useState({ email: "", password: "", fullName: "", phone: "", dateOfBirth: "", curp: "" });
  const [errors, setErrors]   = useState<Record<string, string>>({});

  function validate() {
    const e: Record<string, string> = {};
    if (!form.fullName)                      e.fullName = "Nombre requerido";
    if (!form.email)                         e.email    = "Correo requerido";
    if (form.password.length < 8)            e.password = "Mínimo 8 caracteres";
    if (!/^\+?\d{10,15}$/.test(form.phone.replace(/\s/g, ""))) e.phone = "Teléfono inválido";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    try {
      await authApi.signup(form);
      toast.success("¡Cuenta creada! Revisa tu correo.");
      router.push(`/auth/verify-email?email=${encodeURIComponent(form.email)}`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  const f = (key: string) => ({
    value: form[key as keyof typeof form],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value }),
    error: errors[key],
  });

  return (
    <div className="animate-fade-in">
      <div className="mb-8">
        <h1 className="font-display text-3xl font-bold text-white mb-2">Crear cuenta</h1>
        <p className="text-slate-400">¿Ya tienes cuenta?{" "}
          <Link href="/auth/login" className="text-blue-400 hover:text-blue-300 font-medium">Inicia sesión</Link>
        </p>
      </div>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Nombre completo" type="text" placeholder="Carlos Mendoza" icon={<User size={16} />} {...f("fullName")} required />
        <Input label="Correo electrónico" type="email" placeholder="carlos@ejemplo.com" icon={<Mail size={16} />} {...f("email")} required />
        <Input label="Contraseña" type="password" placeholder="Mínimo 8 caracteres" icon={<Lock size={16} />} {...f("password")} required />
        <Input label="Teléfono" type="tel" placeholder="+52 999 123 4567" icon={<Phone size={16} />} {...f("phone")} required />
        <div className="grid grid-cols-2 gap-4">
          <Input label="Fecha de nacimiento" type="date" {...f("dateOfBirth")} />
          <Input label="CURP (opcional)" type="text" placeholder="XXXX000000HXXXXX00" {...f("curp")} />
        </div>
        <Button type="submit" loading={loading} fullWidth className="mt-1">Crear cuenta gratis</Button>
      </form>
    </div>
  );
}
