"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/store/authStore";
import Sidebar from "@/components/layout/Sidebar";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isLoggedIn, isLoading, initFromStorage } = useAuthStore();

  useEffect(() => { initFromStorage(); }, [initFromStorage]);

  useEffect(() => {
    if (!isLoading && !isLoggedIn) router.push("/auth/login");
  }, [isLoggedIn, isLoading, router]);

  if (isLoading) return (
    <div className="min-h-screen bg-[#07070e] flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-10 h-10 rounded-xl bg-blue-600/20 border border-blue-500/30 flex items-center justify-center">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" strokeWidth="2.5" className="animate-spin" style={{animationDuration:"1.5s"}}>
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4"/>
          </svg>
        </div>
        <p className="text-slate-500 text-sm">Cargando NeoBank…</p>
      </div>
    </div>
  );

  if (!isLoggedIn) return null;

  return (
    <div className="flex min-h-screen bg-[#07070e]">
      <Sidebar />
      <main className="flex-1 flex flex-col min-w-0">
        {children}
      </main>
    </div>
  );
}
