import axios from "axios";
import { api } from "./client";
import type { KycStatus, UploadUrlResponse, ApiResponse, DocumentType } from "@/types";

export const kycApi = {
  getStatus:    ()                                               => api.get<ApiResponse<KycStatus>>("/api/kyc/status"),
  getUploadUrl: (fileName: string, documentType: DocumentType)  =>
    api.get<ApiResponse<UploadUrlResponse>>("/api/kyc/upload-url", { params: { fileName, documentType } }),
  verify:       (documentType: DocumentType, s3Key: string)     => api.post<ApiResponse>("/api/kyc/verify", { documentType, s3Key }),
  uploadToS3:   async (uploadUrl: string, file: File): Promise<void> => {
    await axios.put(uploadUrl, file, {
      headers: { "Content-Type": file.type },
    });
  },
};
