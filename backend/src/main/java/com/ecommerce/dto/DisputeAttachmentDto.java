package com.ecommerce.dto;

import com.ecommerce.model.DisputeAttachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeAttachmentDto {
    private UUID id;
    private String url;
    private String originalName;
    private String contentType;
    private DisputeAttachment.AttachmentType attachmentType;
    private Long sizeBytes;
}
