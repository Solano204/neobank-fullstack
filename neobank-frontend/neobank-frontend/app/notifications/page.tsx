"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import { notificationsApi } from "@/lib/api/notifications";
import type { Notification } from "@/types";
import { Bell, ArrowLeftRight, Shield, AlertCircle, Info, CheckCheck } from "lucide-react";
import { formatDate } from "@/lib/utils";
import toast from "react-hot-toast";

const icons: Record<string, React.ReactNode> = {
  TRANSACTION: <ArrowLeftRight size={18} className="text-blue-400"    />,
  SECURITY:    <Shield         size={18} className="text-red-400"     />,
  KYC:         <AlertCircle   size={18} className="text-yellow-400"  />,
  SYSTEM:      <Info           size={18} className="text-slate-400"   />,
};

export default function NotificationsPage() {
  const [notifications, setNotifs] = useState<Notification[]>([]);
  const [loading, setLoading]      = useState(true);

  useEffect(() => {
    notificationsApi.getAll().then(r => setNotifs(r.data?.notifications || [])).catch(() => {}).finally(() => setLoading(false));
  }, []);

  async function markRead(id: string) {
    try {
      await notificationsApi.markRead(id);
      setNotifs(ns => ns.map(n => n.id === id ? {...n, read: true} : n));
    } catch {}
  }

  async function markAllRead() {
    try {
      await notificationsApi.markAllRead();
      setNotifs(ns => ns.map(n => ({...n, read: true})));
      toast.success("Todas marcadas como leídas");
    } catch {}
  }

  const unread = notifications.filter(n => !n.read).length;

  return (
    <>
      <Header title="Notificaciones" />
      <div className="flex-1 p-6 max-w-2xl mx-auto w-full animate-fade-in">
        <div className="flex items-center justify-between mb-6">
          <p className="text-sm text-slate-400">{unread > 0 ? `${unread} sin leer` : "Todo al día"}</p>
          {unread > 0 && (
            <Button size="sm" variant="ghost" onClick={markAllRead}>
              <CheckCheck size={14} /> Marcar todas como leídas
            </Button>
          )}
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-32"><div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : notifications.length === 0 ? (
          <Card className="flex flex-col items-center py-12 text-slate-500">
            <Bell size={36} className="mb-3 opacity-30" />
            <p className="text-sm">Sin notificaciones</p>
          </Card>
        ) : (
          <div className="flex flex-col gap-2">
            {notifications.map(n => (
              <div key={n.id} onClick={() => !n.read && markRead(n.id)}
                className={`card-neo p-4 flex items-start gap-4 transition-all cursor-pointer ${!n.read ? "border-blue-500/20 hover:border-blue-500/40" : "opacity-70 hover:opacity-100"}`}>
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${!n.read ? "bg-blue-500/10" : "bg-slate-800"}`}>
                  {icons[n.type] || <Bell size={18} className="text-slate-500" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-white">{n.title}</p>
                    {!n.read && <span className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0" />}
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">{n.message}</p>
                  <p className="text-xs text-slate-600 mt-1.5">{formatDate(n.createdAt)}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
