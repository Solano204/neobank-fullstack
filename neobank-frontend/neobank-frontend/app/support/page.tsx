"use client";
import { useState, useRef, useEffect } from "react";
import Header from "@/components/layout/Header";
import Button from "@/components/ui/Button";
import { supportApi }      from "@/lib/api/support";
import { getErrorMessage } from "@/lib/api/client";
import type { ChatMessage } from "@/types";
import { Send, Bot, User } from "lucide-react";
import { formatDate } from "@/lib/utils";

export default function SupportPage() {
  const [messages,   setMessages]   = useState<ChatMessage[]>([{
    id: "0", role: "assistant", timestamp: new Date().toISOString(),
    content: "¡Hola! Soy el asistente de NeoBank. ¿En qué puedo ayudarte?\n\n• Consultar tu saldo\n• Ver transacciones\n• Reportar fraude\n• Preguntas generales",
  }]);
  const [input,     setInput]     = useState("");
  const [loading,   setLoading]   = useState(false);
  const [sessionId, setSessionId] = useState("");
  const bottomRef                 = useRef<HTMLDivElement>(null);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages]);

  async function sendMessage() {
    const text = input.trim();
    if (!text) return;
    setInput("");
    setMessages(m => [...m, { id: Date.now().toString(), role: "user", content: text, timestamp: new Date().toISOString() }]);
    setLoading(true);
    try {
      const res = await supportApi.sendChat(text, sessionId || undefined);
      if (res.data?.sessionId) setSessionId(res.data.sessionId);
      setMessages(m => [...m, { id: (Date.now() + 1).toString(), role: "assistant", content: res.data?.message || "No pude procesar tu solicitud.", timestamp: new Date().toISOString() }]);
    } catch (err) {
      setMessages(m => [...m, { id: (Date.now() + 1).toString(), role: "assistant", content: getErrorMessage(err), timestamp: new Date().toISOString() }]);
    } finally { setLoading(false); }
  }

  return (
    <>
      <Header title="Soporte" />
      <div className="flex-1 flex flex-col max-w-2xl mx-auto w-full p-6 animate-fade-in">
        <div className="card-neo flex-1 flex flex-col overflow-hidden" style={{ maxHeight: "calc(100vh - 200px)" }}>
          {/* Chat header */}
          <div className="flex items-center gap-3 p-4 border-b border-[#1e1e30]">
            <div className="w-10 h-10 rounded-xl bg-blue-600/20 border border-blue-500/30 flex items-center justify-center">
              <Bot size={20} className="text-blue-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">NeoBank Assistant</p>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-soft" />
                <span className="text-xs text-emerald-400">En línea</span>
              </div>
            </div>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
            {messages.map(msg => (
              <div key={msg.id} className={`flex items-start gap-3 ${msg.role === "user" ? "flex-row-reverse" : ""}`}>
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${msg.role === "user" ? "bg-blue-600/20 border border-blue-500/30" : "bg-slate-800"}`}>
                  {msg.role === "user" ? <User size={14} className="text-blue-400" /> : <Bot size={14} className="text-slate-400" />}
                </div>
                <div className={`max-w-[75%] flex flex-col gap-1 ${msg.role === "user" ? "items-end" : "items-start"}`}>
                  <div className={`rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-line ${msg.role === "user" ? "bg-blue-600 text-white rounded-tr-sm" : "bg-[#1a1a2e] border border-[#1e1e30] text-slate-200 rounded-tl-sm"}`}>
                    {msg.content}
                  </div>
                  <span className="text-[10px] text-slate-600">{formatDate(msg.timestamp)}</span>
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center">
                  <Bot size={14} className="text-slate-400" />
                </div>
                <div className="bg-[#1a1a2e] border border-[#1e1e30] rounded-2xl rounded-tl-sm px-4 py-3 flex gap-1">
                  {[0, 1, 2].map(i => (
                    <span key={i} className="w-2 h-2 rounded-full bg-slate-500 animate-pulse-soft"
                      style={{ animationDelay: `${i * 0.15}s` }} />
                  ))}
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Quick prompts */}
          <div className="px-4 pb-2 flex gap-2 flex-wrap">
            {["¿Cuál es mi saldo?", "¿Cómo transferir?", "Reportar fraude"].map(q => (
              <button key={q} onClick={() => setInput(q)}
                className="text-xs px-3 py-1.5 rounded-full bg-[#1a1a2e] border border-[#1e1e30] text-slate-400 hover:text-white hover:border-blue-500/40 transition-all">
                {q}
              </button>
            ))}
          </div>

          {/* Input */}
          <div className="p-4 border-t border-[#1e1e30]">
            <div className="flex gap-3">
              <input className="input-neo flex-1" placeholder="Escribe tu pregunta…"
                value={input} onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendMessage(); } }} />
              <Button onClick={sendMessage} loading={loading} disabled={!input.trim()} className="px-4 flex-shrink-0 w-auto">
                <Send size={18} />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
