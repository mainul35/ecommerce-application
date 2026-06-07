package com.ecommerce.dto;

import com.ecommerce.model.DisputeMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeMessageDto {
    private UUID id;
    private UUID disputeId;
    private UUID senderUserId;
    private String senderName;
    private DisputeMessage.AuthorRole authorRole;
    private String body;
    private List<DisputeAttachmentDto> attachments;
    private LocalDateTime createdAt;
}
