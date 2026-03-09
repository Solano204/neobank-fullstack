"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Input  from "@/components/ui/Input";
import Modal  from "@/components/ui/Modal";
import { contactsApi }     from "@/lib/api/contacts";
import { getErrorMessage } from "@/lib/api/client";
import type { Contact } from "@/types";
import { Users, Star, Trash2, Plus, Send } from "lucide-react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";

export default function ContactsPage() {
  const router = useRouter();
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [modal,    setModal]    = useState(false);
  const [form,     setForm]     = useState({ accountNumber: "", nickname: "" });
  const [saving,   setSaving]   = useState(false);

  useEffect(() => {
    contactsApi.getAll()
      .then(r => setContacts(r.data?.contacts || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  async function addContact() {
    if (form.accountNumber.length !== 18) { toast.error("CLABE debe tener 18 dígitos"); return; }
    setSaving(true);
    try {
      const res = await contactsApi.add(form.accountNumber, form.nickname);
      setContacts(c => [...c, res.data as Contact]);
      toast.success("Contacto agregado");
      setModal(false);
      setForm({ accountNumber: "", nickname: "" });
    } catch (err) { toast.error(getErrorMessage(err)); }
    finally { setSaving(false); }
  }

  async function toggleFav(c: Contact) {
    await contactsApi.toggleFav(c.id, !c.favorite);
    setContacts(cs => cs.map(x => x.id === c.id ? { ...x, favorite: !c.favorite } : x));
  }

  async function remove(id: string) {
    await contactsApi.remove(id);
    setContacts(cs => cs.filter(c => c.id !== id));
    toast.success("Contacto eliminado");
  }

  const favs   = contacts.filter(c =>  c.favorite);
  const others = contacts.filter(c => !c.favorite);

  return (
    <>
      <Header title="Contactos" />
      <div className="flex-1 p-6 max-w-2xl mx-auto w-full animate-fade-in">
        <div className="flex justify-end mb-6">
          <Button onClick={() => setModal(true)}><Plus size={18} /> Agregar contacto</Button>
        </div>

        {loading ? (
          <div className="flex justify-center h-32 items-center">
            <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : contacts.length === 0 ? (
          <Card className="text-center py-12 text-slate-500">
            <Users size={36} className="mx-auto mb-3 opacity-30" />
            <p className="text-sm mb-4">Sin contactos guardados</p>
            <Button size="sm" variant="ghost" onClick={() => setModal(true)}><Plus size={14} /> Agregar primero</Button>
          </Card>
        ) : (
          <>
            {favs.length > 0 && (
              <div className="mb-6">
                <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">Favoritos</h3>
                <div className="flex flex-col gap-2">
                  {favs.map(c => (
                    <ContactRow key={c.id} c={c} onFav={toggleFav} onRemove={remove}
                      onTransfer={() => router.push(`/transfer?to=${c.accountNumber}`)} />
                  ))}
                </div>
              </div>
            )}
            {others.length > 0 && (
              <div>
                <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">Todos los contactos</h3>
                <div className="flex flex-col gap-2">
                  {others.map(c => (
                    <ContactRow key={c.id} c={c} onFav={toggleFav} onRemove={remove}
                      onTransfer={() => router.push(`/transfer?to=${c.accountNumber}`)} />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <Modal open={modal} onClose={() => setModal(false)} title="Nuevo contacto">
        <div className="flex flex-col gap-4">
          <Input label="CLABE (18 dígitos)" type="text" placeholder="000000000000000000"
            value={form.accountNumber}
            onChange={e => setForm({ ...form, accountNumber: e.target.value.replace(/\D/g, "").slice(0, 18) })} />
          <Input label="Apodo (opcional)" placeholder="Mamá, Arrendador…"
            value={form.nickname} onChange={e => setForm({ ...form, nickname: e.target.value })} />
          <Button onClick={addContact} loading={saving} fullWidth>Guardar contacto</Button>
        </div>
      </Modal>
    </>
  );
}

function ContactRow({ c, onFav, onRemove, onTransfer }: {
  c: Contact;
  onFav: (c: Contact) => void;
  onRemove: (id: string) => void;
  onTransfer: () => void;
}) {
  return (
    <Card className="flex items-center gap-4 p-4">
      <div className="w-10 h-10 rounded-full bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-sm font-bold text-blue-400 flex-shrink-0">
        {(c.nickname || c.name || "?")[0].toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-white">{c.nickname || c.name || "Sin nombre"}</p>
        <p className="text-xs text-slate-500 font-mono-neo mt-0.5">••••{c.accountNumber.slice(-4)}</p>
      </div>
      <div className="flex gap-1">
        <button onClick={onTransfer}
          className="p-2 text-blue-400 hover:text-blue-300 hover:bg-blue-500/10 rounded-lg transition-all" title="Transferir">
          <Send size={16} />
        </button>
        <button onClick={() => onFav(c)}
          className={`p-2 rounded-lg transition-all ${c.favorite ? "text-yellow-400 hover:text-yellow-300 hover:bg-yellow-500/10" : "text-slate-500 hover:text-slate-300 hover:bg-white/5"}`}>
          <Star size={16} fill={c.favorite ? "currentColor" : "none"} />
        </button>
        <button onClick={() => onRemove(c.id)}
          className="p-2 text-slate-600 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all">
          <Trash2 size={16} />
        </button>
      </div>
    </Card>
  );
}
