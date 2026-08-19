package com.brainhealth.scale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "visit_attachment")
public class VisitAttachment {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "visit_code", nullable = false, length = 32)
    private String visitCode;

    @Column(name = "field_code", nullable = false, length = 160)
    private String fieldCode;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "content_type", length = 160)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
