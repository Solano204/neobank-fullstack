package neobank.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {
    private KycStatus kycStatus;
    private List<DocumentDto> documents;
    private Integer verificationProgress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDto {
        private DocumentType type;
        private KycStatus status;
        private LocalDateTime uploadedAt;
        private LocalDateTime verifiedAt;
        private BigDecimal aiConfidence;
        private String rejectionReason;
    }
}