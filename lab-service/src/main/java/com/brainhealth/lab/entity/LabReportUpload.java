package com.brainhealth.lab.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "lab_report_upload")
public class LabReportUpload extends BaseEntity {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    @Column(nullable = false, length = 30)
    private String status = "UPLOADED";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
