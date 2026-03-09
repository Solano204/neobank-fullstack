package neobank.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.dto.response.ApiResponse;
import neobank.application.dto.response.KycStatusResponse;
import neobank.application.dto.response.UploadUrlResponse;
import neobank.application.usecase.kyc.DeleteDocumentUseCase;
import neobank.application.usecase.kyc.GenerateUploadUrlUseCase;
import neobank.application.usecase.kyc.GetKycStatusUseCase;
import neobank.application.usecase.kyc.VerifyDocumentUseCase;
import neobank.domain.enums.DocumentType;
import neobank.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@Slf4j
public class KycController {

    private final GenerateUploadUrlUseCase generateUploadUrlUseCase;
    private final VerifyDocumentUseCase verifyDocumentUseCase;
    private final GetKycStatusUseCase getKycStatusUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    @GetMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                       @RequestParam String fileName,
                                                                       @RequestParam DocumentType documentType) {
        log.info("Get upload URL request for user: {} document: {}", userPrincipal.getId(), documentType);

        UploadUrlResponse response = generateUploadUrlUseCase.execute(
                userPrincipal.getId(),
                fileName,
                documentType
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyDocument(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @RequestParam DocumentType documentType,
                                                              @RequestParam String s3Key) {
        log.info("Verify document request for user: {} type: {}", userPrincipal.getId(), documentType);

        verifyDocumentUseCase.execute(userPrincipal.getId(), documentType, s3Key);

        return ResponseEntity.ok(ApiResponse.success("Document submitted for verification"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Get KYC status request for user: {}", userPrincipal.getId());

        KycStatusResponse response = getKycStatusUseCase.execute(userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<String>> deleteDocument(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @PathVariable UUID documentId) {
        log.info("Delete document request: {} for user: {}", documentId, userPrincipal.getId());

        deleteDocumentUseCase.execute(documentId, userPrincipal.getId());

        return ResponseEntity.ok(ApiResponse.success("Document deleted. You can upload a new one."));
    }
}