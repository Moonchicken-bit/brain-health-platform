package com.brainhealth.scale.dto;

import java.time.LocalDate;

public class ScaleProgressDTO {
    private String scaleCode;
    private String scaleName;
    private String status;
    private Double totalScore;
    private LocalDate lastAssessmentDate;

    public String getScaleCode() { return scaleCode; }
    public void setScaleCode(String scaleCode) { this.scaleCode = scaleCode; }
    public String getScaleName() { return scaleName; }
    public void setScaleName(String scaleName) { this.scaleName = scaleName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public LocalDate getLastAssessmentDate() { return lastAssessmentDate; }
    public void setLastAssessmentDate(LocalDate lastAssessmentDate) { this.lastAssessmentDate = lastAssessmentDate; }
}
