package com.brainhealth.scale.dto;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AssessmentDTO {
    private Long id;
    private Long sessionId;
    private Long subjectId;
    private Long instrumentId;
    private Long examinerId;
    private LocalDate assessmentDate;
    private Double totalScore;
    private String dataEntryStatus;
    private String administrationMode;
    private String notes;
    private LocalDateTime createdAt;
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
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
