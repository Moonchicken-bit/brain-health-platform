package com.brainhealth.scale.dto;

/**
 * A single option within a scale item (radio/checkbox/select).
 */
public class OptionDTO {
    private String code;
    private String label;
    private double score;

    public OptionDTO() {}

    public OptionDTO(String code, String label, double score) {
        this.code = code;
        this.label = label;
        this.score = score;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
