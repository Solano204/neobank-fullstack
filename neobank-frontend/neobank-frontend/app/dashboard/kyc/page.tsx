"use client";
import { useEffect, useState, useRef } from "react";
import { Upload, CheckCircle2, Clock, XCircle, FileText, Camera } from "lucide-react";
import { kyc } from "@/lib/api";
import type { KycStatus, DocumentType } from "@/lib/types";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { cn } from "@/lib/utils";

const DOCS: { type: DocumentType; label: string; icon: React.ElementType; desc: string }[] = [
  { type: "INE", label: "INE / IFE", icon: FileText, desc: "Identificación oficial vigente (frente y reverso)" },
  { type: "PASSPORT", label: "Pasaporte", icon: FileText, desc: "Pasaporte mexicano vigente" },
  { type: "SELFIE", label: "Selfie", icon: Camera, desc: "Foto de tu rostro sosteniendo tu ID" },
];

export default function KycPage() {
  const [status, setStatus] = useState<KycStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState<DocumentType | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<DocumentType[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);
  const [currentDoc, setCurrentDoc] = useState<DocumentType | null>(null);

  useEffect(() => {
    kyc.getStatus().then(r => setStatus(r.data ?? null)).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const handleUpload = async (file: File, docType: DocumentType) => {
    setUploading(docType);
    try {
      // 1. Get presigned URL
      const urlRes = await kyc.getUploadUrl(file.name, docType);
      const { uploadUrl, s3Key } = urlRes.data!;

      // 2. Upload to S3
      await kyc.uploadToS3(uploadUrl, file);

      // 3. Notify backend
      await kyc.verify(docType, s3Key);

      setUploadSuccess(prev => [...prev, docType]);

      // Refresh status
      const updated = await kyc.getStatus();
      setStatus(updated.data ?? null);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Error al subir documento");
    } finally {
      setUploading(null);
    }
  };

  const selectFile = (docType: DocumentType) => {
    setCurrentDoc(docType);
    fileRef.current?.click();
  };

  const onFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && currentDoc) handleUpload(file, currentDoc);
    e.target.value = "";
  };

  const docStatus = (docType: DocumentType) => {
    if (uploadSuccess.includes(docType)) return "uploaded";
    return status?.documents?.find(d => d.type === docType)?.status ?? "none";
  };

  const overallStatus = status?.status ?? "PENDING";

  const statusInfo = {
    PENDING: { icon: Clock, color: "text-amber-400", bg: "bg-amber-400/10 border-amber-400/20", label: "Pendiente" },
    IN_PROGRESS: { icon: Clock, color: "text-cyan-400", bg: "bg-cyan-400/10 border-cyan-400/20", label: "En revisión" },
    APPROVED: { icon: CheckCircle2, color: "text-emerald-400", bg: "bg-emerald-400/10 border-emerald-400/20", label: "Aprobado" },
    REJECTED: { icon: XCircle, color: "text-rose-400", bg: "bg-rose-400/10 border-rose-400/20", label: "Rechazado" },
  }[overallStatus] ?? { icon: Clock, color: "text-slate-400", bg: "bg-white/5 border-white/10", label: "—" };

  if (loading) {
    return <div className="space-y-4"><div className="skeleton h-32 rounded-2xl" /><div className="skeleton h-48 rounded-2xl" /></div>;
  }

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <input ref={fileRef} type="file" accept="image/*,application/pdf" className="hidden" onChange={onFileChange} />

      <div>
        <h1 className="font-display font-bold text-2xl text-slate-100">Verificación de identidad</h1>
        <p className="text-slate-500 text-sm mt-1">Necesitamos verificar tu identidad para activar todas las funciones</p>
      </div>

      {/* Status banner */}
      <div className={cn("flex items-center gap-3 px-5 py-4 rounded-2xl border", statusInfo.bg)}>
        <statusInfo.icon size={24} className={statusInfo.color} />
        <div>
          <p className={cn("font-semibold", statusInfo.color)}>Estado: {statusInfo.label}</p>
          {overallStatus === "APPROVED" && <p className="text-sm text-slate-400">Tu cuenta está completamente verificada</p>}
          {overallStatus === "REJECTED" && status?.rejectionReason && (
            <p className="text-sm text-slate-400">{status.rejectionReason}</p>
          )}
          {overallStatus === "PENDING" && <p className="text-sm text-slate-400">Sube tus documentos para comenzar la verificación</p>}
          {overallStatus === "IN_PROGRESS" && <p className="text-sm text-slate-400">Estamos revisando tus documentos (24-48 hrs)</p>}
        </div>
      </div>

      {/* Document upload cards */}
      {overallStatus !== "APPROVED" && (
        <div className="space-y-3">
          <h2 className="font-display font-semibold text-slate-100 text-sm uppercase tracking-wide">Documentos requeridos</h2>
          {DOCS.map(({ type, label, icon: Icon, desc }) => {
            const ds = docStatus(type);
            const isUploading = uploading === type;

            return (
              <Card key={type} glow className={cn(
                "flex items-center gap-4",
                ds === "APPROVED" || ds === "uploaded" ? "opacity-80" : ""
              )}>
                <div className={cn(
                  "w-12 h-12 rounded-xl flex items-center justify-center shrink-0",
                  ds === "APPROVED" || ds === "uploaded" ? "bg-emerald-500/10" : "bg-white/5"
                )}>
                  {ds === "APPROVED" || ds === "uploaded"
                    ? <CheckCircle2 size={22} className="text-emerald-400" />
                    : <Icon size={22} className="text-slate-400" />}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-100">{label}</p>
                  <p className="text-xs text-slate-500">{desc}</p>
                  {ds === "REJECTED" && <p className="text-xs text-rose-400 mt-0.5">Rechazado — vuelve a subir</p>}
                </div>
                {ds === "APPROVED" || ds === "uploaded" ? (
                  <span className="text-xs text-emerald-400 font-medium shrink-0">✓ Subido</span>
                ) : (
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={isUploading}
                    onClick={() => selectFile(type)}
                    className="shrink-0"
                  >
                    <Upload size={14} /> Subir
                  </Button>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {/* Tips */}
      <Card>
        <h3 className="text-sm font-semibold text-slate-100 mb-3">💡 Consejos para una verificación exitosa</h3>
        <ul className="space-y-2 text-xs text-slate-400">
          {[
            "Asegúrate que el documento esté vigente",
            "La imagen debe ser nítida y legible (mín. 800x600px)",
            "Evita reflejos o sombras sobre el documento",
            "El selfie debe mostrar claramente tu rostro y el documento",
            "Acepta formatos: JPG, PNG, PDF",
          ].map(tip => (
            <li key={tip} className="flex items-start gap-2">
              <span className="text-cyan-500 shrink-0">•</span> {tip}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
