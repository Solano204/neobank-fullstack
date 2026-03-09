"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/lib/store/authStore";
import { getInitials } from "@/lib/utils";
import {
  LayoutDashboard, CreditCard, ArrowLeftRight, Send, ShieldCheck,
  Bell, Users, BarChart3, MessageCircle, Settings, LogOut, Zap,
} from "lucide-react";

const nav = [
  { href: "/dashboard",      icon: LayoutDashboard,  label: "Dashboard"      },
  { href: "/accounts",       icon: CreditCard,        label: "Cuentas"        },
  { href: "/transactions",   icon: ArrowLeftRight,    label: "Movimientos"    },
  { href: "/transfer",       icon: Send,              label: "Transferir"     },
  { href: "/kyc",            icon: ShieldCheck,       label: "Verificación"   },
  { href: "/notifications",  icon: Bell,              label: "Notificaciones" },
  { href: "/contacts",       icon: Users,             label: "Contactos"      },
  { href: "/analytics",      icon: BarChart3,         label: "Análisis"       },
  { href: "/support",        icon: MessageCircle,     label: "Soporte"        },
  { href: "/security",       icon: ShieldCheck,       label: "Seguridad"      },
  { href: "/settings",       icon: Settings,          label: "Ajustes"        },
];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuthStore();

  return (
    <aside className="hidden lg:flex flex-col w-64 min-h-screen bg-[#0f0f1a] border-r border-[#1e1e30] p-4">
      {/* Logo */}
      <div className="flex items-center gap-3 px-2 py-4 mb-4">
        <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center flex-shrink-0">
          <Zap size={16} className="text-white" />
        </div>
        <span className="font-display font-bold text-lg text-white">NeoBank</span>
      </div>

      {/* Nav */}
      <nav className="flex-1 flex flex-col gap-0.5">
        {nav.map(({ href, icon: Icon, label }) => (
          <Link key={href} href={href} className={cn("nav-item", pathname === href && "active")}>
            <Icon size={18} />
            <span>{label}</span>
          </Link>
        ))}
      </nav>

      {/* User */}
      {user && (
        <div className="border-t border-[#1e1e30] pt-4 mt-4">
          <div className="flex items-center gap-3 px-2 mb-3">
            <div className="w-9 h-9 rounded-full bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-sm font-bold text-blue-400 flex-shrink-0">
              {getInitials(user.fullName)}
            </div>
            <div className="overflow-hidden">
              <p className="text-sm font-medium text-white truncate">{user.fullName}</p>
              <p className="text-xs text-slate-500 truncate">{user.email}</p>
            </div>
          </div>
          <button onClick={logout} className="nav-item w-full text-red-400 hover:text-red-300 hover:bg-red-500/10">
            <LogOut size={18} />
            <span>Cerrar sesión</span>
          </button>
        </div>
      )}
    </aside>
  );
}
