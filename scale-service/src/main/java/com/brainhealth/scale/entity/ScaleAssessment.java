package com.brainhealth.scale.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scale_assessment")
public class ScaleAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "examiner_id")
    private Long examinerId;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "data_entry_status", length = 30)
    private String dataEntryStatus = "Incomplete";

    @Column(name = "administration_mode", length = 30)
    private String administrationMode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { this.subjectId = v; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long v) { this.instrumentId = v; }
    public Long getExaminerId() { return examinerId; }
    public void setExaminerId(Long v) { this.examinerId = v; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate v) { this.assessmentDate = v; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double v) { this.totalScore = v; }
    public String getDataEntryStatus() { return dataEntryStatus; }
    public void setDataEntryStatus(String v) { this.dataEntryStatus = v; }
    public String getAdministrationMode() { return administrationMode; }
    public void setAdministrationMode(String v) { this.administrationMode = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
