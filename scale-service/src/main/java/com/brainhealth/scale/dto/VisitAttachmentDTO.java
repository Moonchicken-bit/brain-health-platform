package com.brainhealth.scale.dto;

import com.brainhealth.scale.entity.VisitAttachment;

import java.time.LocalDateTime;

public record VisitAttachmentDTO(
        String id,
        String originalName,
        String contentType,
        long size,
        Long subjectId,
        String visitCode,
        String fieldCode,
        LocalDateTime createdAt) {

    public static VisitAttachmentDTO from(VisitAttachment attachment) {
        return new VisitAttachmentDTO(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSize(),
                attachment.getSubjectId(),
                attachment.getVisitCode(),
                attachment.getFieldCode(),
                attachment.getCreatedAt());
    }
}
