import axios from "axios";
import { api } from "./client";
import type { KycStatus, UploadUrlResponse, ApiResponse, DocumentType } from "@/types";

export const kycApi = {
  getStatus:    ()                                               => api.get<ApiResponse<KycStatus>>("/api/kyc/status"),
  getUploadUrl: (fileName: string, documentType: DocumentType)  =>
    api.get<ApiResponse<UploadUrlResponse>>("/api/kyc/upload-url", { params: { fileName, documentType } }),
  // Backend reads documentType/s3Key as @RequestParam (query string), not a JSON body -
  // sending them as a POST body (like every other write here) silently 400s.
  verify: (documentType: DocumentType, s3Key: string) =>
    api.post<ApiResponse>("/api/kyc/verify", undefined, { params: { documentType, s3Key } }),
  deleteDocument: (documentId: string) => api.delete<ApiResponse>(`/api/kyc/documents/${documentId}`),
  uploadToS3:   async (uploadUrl: string, file: File): Promise<void> => {
    await axios.put(uploadUrl, file, {
      headers: { "Content-Type": file.type },
    });
  },
};
