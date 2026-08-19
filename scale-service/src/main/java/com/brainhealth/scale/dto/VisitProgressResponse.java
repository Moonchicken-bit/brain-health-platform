package com.brainhealth.scale.dto;

import java.util.List;

public class VisitProgressResponse {
    private Long subjectId;
    private List<VisitProgressItem> visits;

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public List<VisitProgressItem> getVisits() { return visits; }
    public void setVisits(List<VisitProgressItem> visits) { this.visits = visits; }

    public static class VisitProgressItem {
        private String visitCode;
        private String visitName;
        private List<ScaleProgressDTO> scales;

        public String getVisitCode() { return visitCode; }
        public void setVisitCode(String visitCode) { this.visitCode = visitCode; }
        public String getVisitName() { return visitName; }
        public void setVisitName(String visitName) { this.visitName = visitName; }
        public List<ScaleProgressDTO> getScales() { return scales; }
        public void setScales(List<ScaleProgressDTO> scales) { this.scales = scales; }
    }
}
