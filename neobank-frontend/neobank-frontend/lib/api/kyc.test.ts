import { describe, it, expect, beforeEach, afterEach } from "vitest";
import MockAdapter from "axios-mock-adapter";
import { apiClient } from "./client";
import { kycApi } from "./kyc";

const mock = new MockAdapter(apiClient);

beforeEach(() => mock.reset());
afterEach(() => mock.reset());

describe("kycApi", () => {
  it("getStatus GETs /api/kyc/status", async () => {
    mock.onGet("/api/kyc/status").reply(200, { data: { overallStatus: "PENDING" } });
    const res = await kycApi.getStatus();
    expect(res.data.overallStatus).toBe("PENDING");
  });

  it("getUploadUrl sends fileName/documentType as query params", async () => {
    mock.onGet("/api/kyc/upload-url").reply((config) => {
      expect(config.params).toEqual({ fileName: "selfie.jpg", documentType: "SELFIE" });
      return [200, { data: { uploadUrl: "https://s3/x", s3Key: "k", expiresIn: 300 } }];
    });
    await kycApi.getUploadUrl("selfie.jpg", "SELFIE");
  });

  // KycController's verify() reads documentType/s3Key as @RequestParam (query
  // string), not a JSON body - sending them as a body silently 400s. Pinned
  // here per the source comment documenting this fix.
  it("verify sends documentType/s3Key as query params with no JSON body", async () => {
    mock.onPost("/api/kyc/verify").reply((config) => {
      expect(config.params).toEqual({ documentType: "SELFIE", s3Key: "kyc-docs/x.jpg" });
      expect(config.data).toBeUndefined();
      return [200, { success: true }];
    });
    await kycApi.verify("SELFIE", "kyc-docs/x.jpg");
  });

  it("deleteDocument DELETEs /api/kyc/documents/{id}", async () => {
    mock.onDelete("/api/kyc/documents/doc-1").reply(200, { success: true });
    await kycApi.deleteDocument("doc-1");
  });

  it("uploadToS3 PUTs the raw file with its content-type to the presigned URL", async () => {
    const rawAxiosMock = new MockAdapter((await import("axios")).default);
    rawAxiosMock.onPut("https://s3.example.com/presigned").reply((config) => {
      expect(config.headers?.["Content-Type"]).toBe("image/jpeg");
      return [200, {}];
    });
    const file = new File(["fake-image-bytes"], "selfie.jpg", { type: "image/jpeg" });

    await kycApi.uploadToS3("https://s3.example.com/presigned", file);
    rawAxiosMock.restore();
  });
});
