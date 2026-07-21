// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import KycPage from "./page";

const { getStatus, getUploadUrl, uploadToS3, verify, deleteDocument } = vi.hoisted(() => ({
  getStatus: vi.fn(),
  getUploadUrl: vi.fn(),
  uploadToS3: vi.fn(),
  verify: vi.fn(),
  deleteDocument: vi.fn(),
}));
vi.mock("@/lib/api/kyc", () => ({
  kycApi: { getStatus, getUploadUrl, uploadToS3, verify, deleteDocument },
}));

const { toastSuccess, toastError } = vi.hoisted(() => ({ toastSuccess: vi.fn(), toastError: vi.fn() }));
vi.mock("react-hot-toast", () => ({ default: { success: toastSuccess, error: toastError } }));

const pendingStatus = {
  overallStatus: "PENDING",
  documents: [],
};

const mixedStatus = {
  overallStatus: "IN_PROGRESS",
  documents: [
    { id: "d1", documentType: "INE_FRONT", status: "APPROVED", aiConfidence: 98 },
    { id: "d2", documentType: "INE_BACK",  status: "REJECTED", rejectionReason: "Imagen borrosa" },
    { id: "d3", documentType: "SELFIE",    status: "PENDING" },
  ],
};

beforeEach(() => {
  getStatus.mockReset().mockResolvedValue({ data: pendingStatus });
  getUploadUrl.mockReset().mockResolvedValue({ data: { uploadUrl: "https://s3/upload", s3Key: "key-1" } });
  uploadToS3.mockReset().mockResolvedValue(undefined);
  verify.mockReset().mockResolvedValue({ data: {} });
  deleteDocument.mockReset().mockResolvedValue({ data: {} });
  toastSuccess.mockReset();
  toastError.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

function selectFile(file = new File(["x"], "id.png", { type: "image/png" })) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
}

describe("KycPage", () => {
  it("shows a spinner while loading, then the document checklist", async () => {
    render(<KycPage />);
    expect(document.querySelector(".animate-spin")).toBeInTheDocument();
    expect(await screen.findByText("INE / IFE (frente)")).toBeInTheDocument();
    expect(screen.getByText("Selfie")).toBeInTheDocument();
    expect(screen.getByText("Comprobante de domicilio")).toBeInTheDocument();
  });

  it("shows the Pendiente badge and generic instructions when overallStatus is PENDING", async () => {
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");
    expect(screen.getByText("Pendiente")).toBeInTheDocument();
    expect(screen.getByText(/Sube tus documentos/)).toBeInTheDocument();
  });

  it("shows the verified message and badge when overallStatus is APPROVED", async () => {
    getStatus.mockResolvedValue({ data: { overallStatus: "APPROVED", documents: [] } });
    render(<KycPage />);
    expect(await screen.findByText("Verificado ✓")).toBeInTheDocument();
    expect(screen.getByText(/Tu identidad ha sido verificada/)).toBeInTheDocument();
  });

  it("renders per-document status: approved icon hides action buttons, rejected shows reason + Reintentar", async () => {
    getStatus.mockResolvedValue({ data: mixedStatus });
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");

    expect(screen.getByText("Imagen borrosa")).toBeInTheDocument();
    expect(screen.getByText("Confianza IA: 98%")).toBeInTheDocument();
    expect(screen.getAllByText("Reintentar").length).toBeGreaterThan(0);
    expect(screen.getByText("Subir")).toBeInTheDocument(); // PROOF_OF_ADDRESS has no doc yet
  });

  it("does not show delete/upload actions for an already-approved document", async () => {
    getStatus.mockResolvedValue({ data: mixedStatus });
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");
    const ineFrontCard = screen.getByText("INE / IFE (frente)").closest("div")!.parentElement!;
    expect(ineFrontCard.querySelector("button")).not.toBeInTheDocument();
  });

  it("uploading a document runs the full presign → S3 PUT → verify → refresh-status flow", async () => {
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");

    fireEvent.click(screen.getAllByText("Subir")[0]);
    selectFile();

    await waitFor(() => expect(verify).toHaveBeenCalledWith("INE_FRONT", "key-1"));
    expect(getUploadUrl).toHaveBeenCalledWith("id.png", "INE_FRONT");
    expect(uploadToS3).toHaveBeenCalledWith("https://s3/upload", expect.any(File));
    expect(toastSuccess).toHaveBeenCalledWith("Documento enviado para verificación");
    expect(getStatus).toHaveBeenCalledTimes(2); // initial load + post-upload refresh
  });

  it("a failed upload shows the server error toast", async () => {
    verify.mockRejectedValue({ response: { data: { message: "Documento inválido" } }, isAxiosError: true });
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");

    fireEvent.click(screen.getAllByText("Subir")[0]);
    selectFile();

    await waitFor(() => expect(toastError).toHaveBeenCalledWith("Documento inválido"));
  });

  it("deleting a document calls the API and refreshes status", async () => {
    getStatus.mockResolvedValue({ data: mixedStatus });
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");

    const rejectedCard = screen.getByText("Imagen borrosa").closest("div")!.parentElement!.parentElement!;
    const deleteButton = rejectedCard.querySelectorAll("button")[0];
    fireEvent.click(deleteButton);

    await waitFor(() => expect(deleteDocument).toHaveBeenCalledWith("d2"));
    expect(toastSuccess).toHaveBeenCalledWith("Documento eliminado");
  });

  it("selecting no file (dialog cancelled) does not trigger an upload", async () => {
    render(<KycPage />);
    await screen.findByText("INE / IFE (frente)");
    fireEvent.click(screen.getAllByText("Subir")[0]);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [] } });

    expect(getUploadUrl).not.toHaveBeenCalled();
  });
});
