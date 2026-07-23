package neobank.application.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlResponse {
    private String uploadUrl;
    // Was missing entirely - the frontend's kyc/page.tsx already reads
    // `urlRes.data.s3Key` to pass back to POST /api/kyc/verify, so this
    // field's absence meant every KYC upload silently sent "undefined" as
    // the verify call's s3Key. Confirmed by reading the frontend before
    // concluding this, not assumed.
    private String s3Key;
    private Integer expiresIn;
    private String documentType;
    private Long maxFileSize;
}