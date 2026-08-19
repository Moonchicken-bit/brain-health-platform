package com.brainhealth.scale.dto;

import java.util.Map;

public class ComputeResponse {
    private Double totalScore;
    private Double maxScore;
    private Map<String, Double> subscaleScores;
    private String interpretation;

    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public Double getMaxScore() { return maxScore; }
    public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }
    public Map<String, Double> getSubscaleScores() { return subscaleScores; }
    public void setSubscaleScores(Map<String, Double> subscaleScores) { this.subscaleScores = subscaleScores; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
}
