package com.brainhealth.imaging.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "imaging_processing_task")
public class ImagingProcessingTask {
    @Id @Column(name = "task_id", length = 80)
    private String taskId;
    @Column(name = "imaging_session_id", nullable = false)
    private Long imagingSessionId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(nullable = false, length = 32)
    private String kind;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false)
    private Integer progress;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String logs;
    @Column(name = "output_prefix", length = 512)
    private String outputPrefix;
    @Lob @Column(columnDefinition = "TEXT")
    private String error;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String v) { taskId = v; }
    public Long getImagingSessionId() { return imagingSessionId; }
    public void setImagingSessionId(Long v) { imagingSessionId = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { kind = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer v) { progress = v; }
    public String getLogs() { return logs; }
    public void setLogs(String v) { logs = v; }
    public String getOutputPrefix() { return outputPrefix; }
    public void setOutputPrefix(String v) { outputPrefix = v; }
    public String getError() { return error; }
    public void setError(String v) { error = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
