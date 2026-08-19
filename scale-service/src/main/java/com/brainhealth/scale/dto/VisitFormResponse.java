package com.brainhealth.scale.dto;

import java.util.List;

public class VisitFormResponse {
    private String visitCode;
    private String visitName;
    private List<ScaleFormDTO> scales;

    public String getVisitCode() { return visitCode; }
    public void setVisitCode(String visitCode) { this.visitCode = visitCode; }
    public String getVisitName() { return visitName; }
    public void setVisitName(String visitName) { this.visitName = visitName; }
    public List<ScaleFormDTO> getScales() { return scales; }
    public void setScales(List<ScaleFormDTO> scales) { this.scales = scales; }
}
