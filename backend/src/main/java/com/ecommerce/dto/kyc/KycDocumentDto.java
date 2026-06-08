package com.ecommerce.dto.kyc;

import com.ecommerce.model.KycDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocumentDto {
    private UUID id;
    private UUID caseId;
    private KycDocument.KycDocType docType;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}
