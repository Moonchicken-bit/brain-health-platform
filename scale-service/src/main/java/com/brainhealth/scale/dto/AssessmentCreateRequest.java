package com.brainhealth.scale.dto;
import java.time.LocalDate;

public class AssessmentCreateRequest {
    private Long instrumentId;
    private Long subjectId;
    private Long sessionId;
    private Long examinerId;
    private LocalDate assessmentDate;
    private String administrationMode;
    private String notes;
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long v) { this.instrumentId = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { this.subjectId = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public Long getExaminerId() { return examinerId; }
    public void setExaminerId(Long v) { this.examinerId = v; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate v) { this.assessmentDate = v; }
    public String getAdministrationMode() { return administrationMode; }
    public void setAdministrationMode(String v) { this.administrationMode = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
