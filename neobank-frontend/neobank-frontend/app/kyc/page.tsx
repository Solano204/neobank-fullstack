"use client";
import { useState, useEffect, useRef } from "react";
import Header from "@/components/layout/Header";
import Card   from "@/components/ui/Card";
import Badge  from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { kycApi }          from "@/lib/api/kyc";
import { getErrorMessage } from "@/lib/api/client";
import type { KycStatus, DocumentType } from "@/types";
import { Upload, CheckCircle, XCircle, Clock, Shield, FileText } from "lucide-react";
import toast from "react-hot-toast";

const DOCS: { type: DocumentType; label: string; description: string }[] = [
  { type: "INE_FRONT",        label: "INE / IFE (frente)",        description: "Parte frontal de tu identificación oficial"       },
  { type: "INE_BACK",         label: "INE / IFE (reverso)",       description: "Parte trasera de tu identificación oficial"       },
  { type: "SELFIE",           label: "Selfie",                    description: "Foto de tu rostro con buena iluminación"         },
  { type: "PROOF_OF_ADDRESS", label: "Comprobante de domicilio",  description: "Recibo de servicios, máximo 3 meses de antigüedad"},
];

export default function KycPage() {
  const [status,    setStatus]    = useState<KycStatus | null>(null);
  const [loading,   setLoading]   = useState(true);
  const [uploading, setUploading] = useState<DocumentType | null>(null);
  const [pending,   setPending]   = useState<DocumentType | null>(null);
  const fileInputRef              = useRef<HTMLInputElement>(null);

  useEffect(() => {
    kycApi.getStatus().then(r => setStatus(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);

  async function handleUpload(docType: DocumentType, file: File) {
    setUploading(docType);
    try {
      const urlRes = await kycApi.getUploadUrl(file.name, docType);
      await kycApi.uploadToS3(urlRes.data.uploadUrl, file);
      await kycApi.verify(docType, urlRes.data.s3Key);
      toast.success("Documento enviado para verificación");
      const updated = await kycApi.getStatus();
      setStatus(updated.data);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally { setUploading(null); }
  }

  function triggerUpload(docType: DocumentType) {
    setPending(docType);
    fileInputRef.current?.click();
  }

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file && pending) handleUpload(pending, file);
    e.target.value = "";
  }

  function docStatus(type: DocumentType) {
    return status?.documents.find(d => d.documentType === type);
  }

  const overallBadge = {
    APPROVED:    <Badge variant="success">Verificado ✓</Badge>,
    IN_PROGRESS: <Badge variant="pending">En revisión</Badge>,
    REJECTED:    <Badge variant="failed">Rechazado</Badge>,
    PENDING:     <Badge variant="info">Pendiente</Badge>,
  }[status?.overallStatus || "PENDING"];

  return (
    <>
      <Header title="Verificación KYC" />
      <div className="flex-1 p-6 max-w-2xl mx-auto w-full animate-fade-in">
        <input ref={fileInputRef} type="file" accept="image/*,application/pdf" className="hidden" onChange={onFileChange} />

        <Card className="flex items-center gap-5 mb-6">
          <div className="w-14 h-14 rounded-2xl bg-blue-600/15 border border-blue-500/25 flex items-center justify-center flex-shrink-0">
            <Shield size={26} className="text-blue-400" />
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-1">
              <h2 className="font-display font-semibold text-white">Estado de verificación</h2>
              {overallBadge}
            </div>
            <p className="text-sm text-slate-400">
              {status?.overallStatus === "APPROVED"
                ? "Tu identidad ha sido verificada. Tienes acceso a todos los límites."
                : "Sube tus documentos para verificar tu identidad y desbloquear más funciones."}
            </p>
          </div>
        </Card>

        {loading ? (
          <div className="flex items-center justify-center h-32">
            <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            {DOCS.map(doc => {
              const d           = docStatus(doc.type);
              const isUploading = uploading === doc.type;
              return (
                <Card key={doc.type} className="flex items-center gap-4">
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${d?.status === "APPROVED" ? "bg-emerald-500/10" : d?.status === "REJECTED" ? "bg-red-500/10" : "bg-slate-800"}`}>
                    {d?.status === "APPROVED" ? <CheckCircle size={22} className="text-emerald-400" />
                     : d?.status === "REJECTED" ? <XCircle size={22} className="text-red-400" />
                     : d?.status === "PENDING"  ? <Clock    size={22} className="text-yellow-400" />
                     : <FileText size={22} className="text-slate-500" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-white">{doc.label}</p>
                    <p className="text-xs text-slate-500 mt-0.5">{d?.rejectionReason || doc.description}</p>
                    {d?.aiConfidence && <p className="text-xs text-blue-400 mt-0.5">Confianza IA: {d.aiConfidence}%</p>}
                  </div>
                  {d?.status !== "APPROVED" && (
                    <Button size="sm" variant={d?.status === "REJECTED" ? "danger" : "ghost"}
                      loading={isUploading} onClick={() => triggerUpload(doc.type)}>
                      <Upload size={14} />
                      {d ? "Reintentar" : "Subir"}
                    </Button>
                  )}
                </Card>
              );
            })}
          </div>
        )}

        <p className="mt-6 text-center text-xs text-slate-600">
          Tus documentos son procesados con cifrado de extremo a extremo y almacenados de forma segura en AWS S3.
        </p>
      </div>
    </>
  );
}
