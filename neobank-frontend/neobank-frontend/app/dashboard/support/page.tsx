"use client";
import { useEffect, useRef, useState } from "react";
import { Send, MessageCircle, Bot, User, Plus, ChevronDown } from "lucide-react";
import { support } from "@/lib/api";
import { timeAgo } from "@/lib/utils";
import type { ChatMessage, SupportTicket } from "@/lib/types";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";

const QUICK_QUESTIONS = [
  "¿Cómo hago una transferencia?",
  "¿Cuál es mi saldo?",
  "¿Cómo verifico mi identidad?",
  "No reconozco un cargo",
  "¿Cómo activo el 2FA?",
];

export default function SupportPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "0",
      role: "assistant",
      content: "¡Hola! Soy el asistente virtual de NeoBank. ¿En qué puedo ayudarte hoy?",
      timestamp: new Date().toISOString(),
    },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<string | undefined>();
  const [showTicket, setShowTicket] = useState(false);
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [ticketForm, setTicketForm] = useState({ subject: "", description: "" });
  const [submittingTicket, setSubmittingTicket] = useState(false);
  const [faq, setFaq] = useState<{ question: string; answer: string }[]>([]);
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    support.getFaq().then(r => setFaq(r.data ?? [])).catch(() => {});
    support.getTickets().then(r => setTickets(r.data ?? [])).catch(() => {});
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const sendMessage = async (text: string) => {
    if (!text.trim() || sending) return;
    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: "user",
      content: text.trim(),
      timestamp: new Date().toISOString(),
    };
    setMessages(prev => [...prev, userMsg]);
    setInput("");
    setSending(true);

    try {
      const res = await support.sendMessage({ message: text.trim(), sessionId });
      if (res.data?.sessionId) setSessionId(res.data.sessionId);
      const botMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content: res.data?.reply ?? "Gracias por tu mensaje. Un agente te atenderá pronto.",
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, botMsg]);
    } catch {
      const errMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content: "Lo siento, no pude procesar tu mensaje. Por favor intenta de nuevo o crea un ticket de soporte.",
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errMsg]);
    } finally {
      setSending(false);
    }
  };

  const submitTicket = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmittingTicket(true);
    try {
      const res = await support.createTicket(ticketForm.subject, ticketForm.description);
      if (res.data) setTickets(prev => [res.data!, ...prev]);
      setTicketForm({ subject: "", description: "" });
      setShowTicket(false);
    } catch { } finally {
      setSubmittingTicket(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="font-display font-bold text-2xl text-slate-100">Soporte</h1>
        <p className="text-slate-500 text-sm mt-1">Asistente virtual disponible 24/7</p>
      </div>

      {/* Chat */}
      <div className="glass rounded-2xl border border-white/6 flex flex-col h-[480px]">
        {/* Chat header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-white/5">
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-400 to-emerald-400 flex items-center justify-center">
            <Bot size={16} className="text-navy-950" />
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-100">Asistente NeoBank</p>
            <div className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              <p className="text-xs text-slate-500">En línea</p>
            </div>
          </div>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {messages.map(m => (
            <div key={m.id} className={`flex items-end gap-2 ${m.role === "user" ? "justify-end" : "justify-start"}`}>
              {m.role === "assistant" && (
                <div className="w-7 h-7 rounded-full bg-gradient-to-br from-cyan-400/30 to-emerald-400/30 flex items-center justify-center shrink-0">
                  <Bot size={13} className="text-cyan-400" />
                </div>
              )}
              <div className={`max-w-[78%] rounded-2xl px-4 py-2.5 text-sm ${
                m.role === "user"
                  ? "bg-cyan-500 text-white rounded-br-md"
                  : "bg-navy-800 border border-white/8 text-slate-100 rounded-bl-md"
              }`}>
                <p className="leading-relaxed">{m.content}</p>
                <p className={`text-xs mt-1 ${m.role === "user" ? "text-white/60" : "text-slate-600"}`}>
                  {timeAgo(m.timestamp)}
                </p>
              </div>
              {m.role === "user" && (
                <div className="w-7 h-7 rounded-full bg-navy-700 flex items-center justify-center shrink-0">
                  <User size={13} className="text-slate-400" />
                </div>
              )}
            </div>
          ))}
          {sending && (
            <div className="flex items-end gap-2">
              <div className="w-7 h-7 rounded-full bg-gradient-to-br from-cyan-400/30 to-emerald-400/30 flex items-center justify-center">
                <Bot size={13} className="text-cyan-400" />
              </div>
              <div className="bg-navy-800 border border-white/8 rounded-2xl rounded-bl-md px-4 py-3 flex gap-1">
                {[0,1,2].map(i => (
                  <span key={i} className="w-1.5 h-1.5 rounded-full bg-slate-500 animate-pulse" style={{ animationDelay: `${i * 0.2}s` }} />
                ))}
              </div>
            </div>
          )}
          <div ref={bottomRef} />
        </div>

        {/* Quick questions */}
        <div className="px-4 py-2 border-t border-white/5 flex gap-2 overflow-x-auto">
          {QUICK_QUESTIONS.map(q => (
            <button
              key={q}
              onClick={() => sendMessage(q)}
              className="text-xs px-3 py-1.5 rounded-lg bg-navy-800 text-slate-300 border border-white/10 hover:border-cyan-500/30 hover:text-cyan-300 whitespace-nowrap transition-all shrink-0"
            >
              {q}
            </button>
          ))}
        </div>

        {/* Input */}
        <div className="p-3 border-t border-white/5 flex gap-2">
          <input
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === "Enter" && sendMessage(input)}
            placeholder="Escribe tu pregunta..."
            className="flex-1 h-10 bg-navy-800 border border-white/10 rounded-xl text-slate-100 px-4 text-sm focus:border-cyan-500/60 transition-all placeholder-slate-600"
          />
          <button
            onClick={() => sendMessage(input)}
            disabled={!input.trim() || sending}
            className="w-10 h-10 rounded-xl bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 flex items-center justify-center transition-all shrink-0"
          >
            <Send size={16} className="text-navy-950" />
          </button>
        </div>
      </div>

      {/* Create ticket */}
      <Card>
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-display font-semibold text-slate-100 text-sm">¿Necesitas más ayuda?</h2>
          <Button size="sm" variant="secondary" onClick={() => setShowTicket(v => !v)}>
            <Plus size={14} /> Crear ticket
          </Button>
        </div>
        <p className="text-xs text-slate-500 mb-4">Un agente te responderá en menos de 24 horas</p>

        {showTicket && (
          <form onSubmit={submitTicket} className="space-y-3 border-t border-white/5 pt-4">
            <Input
              label="Asunto"
              value={ticketForm.subject}
              onChange={e => setTicketForm(f => ({ ...f, subject: e.target.value }))}
              placeholder="Describe brevemente el problema"
              required
            />
            <div>
              <label className="text-sm font-medium text-slate-300 block mb-1.5">Descripción</label>
              <textarea
                value={ticketForm.description}
                onChange={e => setTicketForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Describe con detalle tu problema o consulta..."
                rows={3}
                className="w-full bg-navy-800/80 border border-white/10 rounded-xl text-slate-100 p-3 text-sm focus:border-cyan-500/60 transition-all placeholder-slate-600 resize-none"
                required
              />
            </div>
            <Button type="submit" loading={submittingTicket} className="w-full">Enviar ticket</Button>
          </form>
        )}

        {/* Existing tickets */}
        {tickets.length > 0 && (
          <div className="mt-4 space-y-2">
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Tus tickets</p>
            {tickets.map(t => (
              <div key={t.id} className="flex items-center gap-3 p-3 rounded-xl bg-white/3 border border-white/5">
                <MessageCircle size={14} className="text-slate-400 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-slate-100 font-medium truncate">{t.subject}</p>
                  <p className="text-xs text-slate-500">{timeAgo(t.createdAt)}</p>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  t.status === "RESOLVED" ? "bg-emerald-500/20 text-emerald-400" :
                  t.status === "IN_PROGRESS" ? "bg-cyan-500/20 text-cyan-400" :
                  "bg-amber-500/20 text-amber-400"
                }`}>
                  {t.status === "RESOLVED" ? "Resuelto" : t.status === "IN_PROGRESS" ? "En proceso" : "Abierto"}
                </span>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* FAQ */}
      {faq.length > 0 && (
        <Card>
          <h2 className="font-display font-semibold text-slate-100 text-sm mb-4">Preguntas frecuentes</h2>
          <div className="space-y-2">
            {faq.map((item, i) => (
              <div key={i} className="border border-white/8 rounded-xl overflow-hidden">
                <button
                  onClick={() => setOpenFaq(openFaq === i ? null : i)}
                  className="w-full flex items-center justify-between px-4 py-3 text-sm text-slate-200 hover:bg-white/3 transition-colors text-left"
                >
                  {item.question}
                  <ChevronDown size={14} className={`shrink-0 text-slate-500 transition-transform ${openFaq === i ? "rotate-180" : ""}`} />
                </button>
                {openFaq === i && (
                  <div className="px-4 pb-3 text-xs text-slate-400 border-t border-white/5 pt-3">
                    {item.answer}
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
