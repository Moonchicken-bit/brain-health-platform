package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SessionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String subjectId;
    private String visitLabel;
    private Integer visitNumber;
    private LocalDateTime sessionDate;
    private String status;

    public SessionDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getVisitLabel() {
        return visitLabel;
    }

    public void setVisitLabel(String visitLabel) {
        this.visitLabel = visitLabel;
    }

    public Integer getVisitNumber() {
        return visitNumber;
    }

    public void setVisitNumber(Integer visitNumber) {
        this.visitNumber = visitNumber;
    }

    public LocalDateTime getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDateTime sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
