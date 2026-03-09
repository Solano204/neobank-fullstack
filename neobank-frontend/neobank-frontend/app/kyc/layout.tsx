"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/store/authStore";
import Sidebar from "@/components/layout/Sidebar";

export default function RouteLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isLoggedIn, isLoading, initFromStorage } = useAuthStore();
  useEffect(() => { initFromStorage(); }, [initFromStorage]);
  useEffect(() => { if (!isLoading && !isLoggedIn) router.push("/auth/login"); }, [isLoggedIn, isLoading, router]);
  if (isLoading) return <div className="min-h-screen bg-[#07070e] flex items-center justify-center"><div className="w-7 h-7 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  if (!isLoggedIn) return null;
  return (
    <div className="flex min-h-screen bg-[#07070e]">
      <Sidebar />
      <main className="flex-1 flex flex-col min-w-0">{children}</main>
    </div>
  );
}
