"use client";
import { useEffect, useState } from "react";
import { Plus, Star, Trash2, Search, User } from "lucide-react";
import { contacts } from "@/lib/api";
import { isValidClabe, cn } from "@/lib/utils";
import type { Contact } from "@/lib/types";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";

export default function ContactsPage() {
  const [list, setList] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState({ accountNumber: "", nickname: "" });
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const load = () => {
    contacts.getAll().then(r => setList(r.data ?? [])).catch(() => {}).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const filtered = list.filter(c =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.nickname?.toLowerCase().includes(search.toLowerCase()) ||
    c.accountNumber.includes(search)
  );

  const favorites = filtered.filter(c => c.favorite);
  const others = filtered.filter(c => !c.favorite);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValidClabe(form.accountNumber)) { setError("CLABE inválida (18 dígitos)"); return; }
    setError("");
    setAdding(true);
    try {
      await contacts.add(form);
      setShowAdd(false);
      setForm({ accountNumber: "", nickname: "" });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al agregar contacto");
    } finally {
      setAdding(false);
    }
  };

  const toggleFav = async (id: string, current: boolean) => {
    await contacts.toggleFavorite(id, !current).catch(() => {});
    setList(prev => prev.map(c => c.id === id ? { ...c, favorite: !c.favorite } : c));
  };

  const remove = async (id: string) => {
    if (!confirm("¿Eliminar este contacto?")) return;
    await contacts.remove(id).catch(() => {});
    setList(prev => prev.filter(c => c.id !== id));
  };

  const ContactItem = ({ c }: { c: Contact }) => (
    <div className="flex items-center gap-3 p-3 rounded-xl hover:bg-white/3 transition-colors group">
      <div className="w-10 h-10 rounded-full bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 border border-white/10 flex items-center justify-center shrink-0">
        <User size={16} className="text-slate-400" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-slate-100 font-medium">{c.nickname ?? c.name}</p>
        <p className="text-xs text-slate-500 font-mono">•••• {c.accountNumber.slice(-4)}</p>
      </div>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={() => toggleFav(c.id, c.favorite)}
          className={cn("p-1.5 rounded-lg transition-colors", c.favorite ? "text-amber-400" : "text-slate-600 hover:text-amber-400")}
        >
          <Star size={14} fill={c.favorite ? "currentColor" : "none"} />
        </button>
        <button onClick={() => remove(c.id)} className="p-1.5 rounded-lg text-slate-600 hover:text-rose-400 transition-colors">
          <Trash2 size={14} />
        </button>
      </div>
    </div>
  );

  return (
    <div className="max-w-lg mx-auto space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="font-display font-bold text-2xl text-slate-100">Contactos</h1>
        <Button size="sm" onClick={() => setShowAdd(v => !v)}>
          <Plus size={14} /> Agregar
        </Button>
      </div>

      {/* Add form */}
      {showAdd && (
        <Card className="animate-fade-up">
          <h2 className="font-display font-semibold text-slate-100 text-sm mb-4">Nuevo contacto</h2>
          <form onSubmit={handleAdd} className="space-y-3">
            <Input
              label="CLABE (18 dígitos)"
              type="text"
              inputMode="numeric"
              placeholder="012345678901234567"
              value={form.accountNumber}
              onChange={e => setForm(f => ({ ...f, accountNumber: e.target.value.replace(/\D/g, "").slice(0, 18) }))}
              error={error}
              required
            />
            <Input
              label="Alias (opcional)"
              type="text"
              placeholder="Ej: Arrendador, Mamá..."
              value={form.nickname}
              onChange={e => setForm(f => ({ ...f, nickname: e.target.value }))}
            />
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" className="flex-1" type="button" onClick={() => setShowAdd(false)}>Cancelar</Button>
              <Button size="sm" loading={adding} className="flex-1" type="submit">Guardar</Button>
            </div>
          </form>
        </Card>
      )}

      {/* Search */}
      <div className="relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
        <input
          type="text"
          placeholder="Buscar contactos..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full h-11 bg-navy-800/80 border border-white/10 rounded-xl text-slate-100 pl-10 pr-4 text-sm focus:border-cyan-500/60 transition-all placeholder-slate-600"
        />
      </div>

      {loading ? (
        <div className="space-y-2">{[1,2,3,4].map(i => <div key={i} className="skeleton h-14 rounded-xl" />)}</div>
      ) : list.length === 0 ? (
        <Card className="text-center py-10">
          <User size={32} className="text-slate-700 mx-auto mb-3" />
          <p className="text-slate-500 text-sm">No tienes contactos guardados</p>
          <p className="text-slate-600 text-xs mt-1">Agrega un contacto para transferir rápidamente</p>
        </Card>
      ) : (
        <Card className="p-0 overflow-hidden">
          {favorites.length > 0 && (
            <>
              <div className="px-4 pt-4 pb-1">
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wide flex items-center gap-1.5">
                  <Star size={10} className="text-amber-400" fill="currentColor" /> Favoritos
                </p>
              </div>
              {favorites.map(c => <ContactItem key={c.id} c={c} />)}
            </>
          )}
          {others.length > 0 && (
            <>
              <div className="px-4 pt-4 pb-1 border-t border-white/5">
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Todos</p>
              </div>
              {others.map(c => <ContactItem key={c.id} c={c} />)}
            </>
          )}
        </Card>
      )}
    </div>
  );
}
