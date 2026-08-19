package com.brainhealth.exportz.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_request")
public class ExportRequest extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, unique = true, length = 50)
    private String requestId;
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;
    @Column(name = "requester_name", length = 100)
    private String requesterName;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "project_name", length = 200)
    private String projectName;
    @Column(name = "export_type", length = 50)
    private String exportType;
    @Column(name = "format_type", length = 20)
    private String formatType;
    @Column(name = "data_scope_summary", columnDefinition = "TEXT")
    private String dataScopeSummary;
    @Column(length = 30)
    private String status = "PENDING";
    @Column(name = "file_url", length = 500)
    private String fileUrl;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "total_records")
    private Integer totalRecords;
    @Column(columnDefinition = "TEXT")
    private String reason;
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;
    @Column(name = "reviewer_id")
    private Long reviewerId;
    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getRequestId() { return requestId; } public void setRequestId(String v) { requestId = v; }
    public Long getRequesterId() { return requesterId; } public void setRequesterId(Long v) { requesterId = v; }
    public String getRequesterName() { return requesterName; } public void setRequesterName(String v) { requesterName = v; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long v) { projectId = v; }
    public String getProjectName() { return projectName; } public void setProjectName(String v) { projectName = v; }
    public String getExportType() { return exportType; } public void setExportType(String v) { exportType = v; }
    public String getFormatType() { return formatType; } public void setFormatType(String v) { formatType = v; }
    public String getDataScopeSummary() { return dataScopeSummary; } public void setDataScopeSummary(String v) { dataScopeSummary = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getFileUrl() { return fileUrl; } public void setFileUrl(String v) { fileUrl = v; }
    public Long getFileSize() { return fileSize; } public void setFileSize(Long v) { fileSize = v; }
    public Integer getTotalRecords() { return totalRecords; } public void setTotalRecords(Integer v) { totalRecords = v; }
    public String getReason() { return reason; } public void setReason(String v) { reason = v; }
    public String getReviewComment() { return reviewComment; } public void setReviewComment(String v) { reviewComment = v; }
    public Long getReviewerId() { return reviewerId; } public void setReviewerId(Long v) { reviewerId = v; }
    public String getReviewerName() { return reviewerName; } public void setReviewerName(String v) { reviewerName = v; }
    public LocalDateTime getReviewedAt() { return reviewedAt; } public void setReviewedAt(LocalDateTime v) { reviewedAt = v; }
}
