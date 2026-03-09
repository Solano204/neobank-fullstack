"use client";
import { useAuthStore } from "@/lib/store/authStore";
import { Bell, Menu } from "lucide-react";
import Link from "next/link";
import { getInitials } from "@/lib/utils";

interface HeaderProps { title: string; }

export default function Header({ title }: HeaderProps) {
  const { user } = useAuthStore();

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between h-16 px-6 bg-[#07070e]/80 backdrop-blur-md border-b border-[#1e1e30]">
      <div className="flex items-center gap-4">
        <button className="lg:hidden text-slate-400 hover:text-white"><Menu size={22} /></button>
        <h1 className="font-display font-semibold text-white text-xl">{title}</h1>
      </div>
      <div className="flex items-center gap-3">
        <Link href="/notifications" className="relative w-9 h-9 flex items-center justify-center rounded-xl hover:bg-white/5 text-slate-400 hover:text-white transition-colors">
          <Bell size={20} />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-blue-500 rounded-full" />
        </Link>
        <Link href="/settings" className="w-9 h-9 rounded-full bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-sm font-bold text-blue-400 hover:bg-blue-600/30 transition-colors">
          {user ? getInitials(user.fullName) : "?"}
        </Link>
      </div>
    </header>
  );
}
