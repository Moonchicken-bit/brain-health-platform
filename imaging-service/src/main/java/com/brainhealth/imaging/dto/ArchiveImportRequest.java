package com.brainhealth.imaging.dto;

import java.time.LocalDate;
import java.util.List;

public class ArchiveImportRequest {
    private Long subjectId;
    private Long sessionId;
    private Long modalityId;
    private LocalDate acquisitionDate;
    private String sourceObject;
    private List<Series> series;

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Long getModalityId() { return modalityId; }
    public void setModalityId(Long v) { modalityId = v; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate v) { acquisitionDate = v; }
    public String getSourceObject() { return sourceObject; }
    public void setSourceObject(String v) { sourceObject = v; }
    public List<Series> getSeries() { return series; }
    public void setSeries(List<Series> v) { series = v; }

    public static class Series {
        private String seriesInstanceUid;
        private Integer seriesNumber;
        private String description;
        private String modality;
        private Integer fileCount;
        private String previewBase64;
        public String getSeriesInstanceUid() { return seriesInstanceUid; }
        public void setSeriesInstanceUid(String v) { seriesInstanceUid = v; }
        public Integer getSeriesNumber() { return seriesNumber; }
        public void setSeriesNumber(Integer v) { seriesNumber = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { description = v; }
        public String getModality() { return modality; }
        public void setModality(String v) { modality = v; }
        public Integer getFileCount() { return fileCount; }
        public void setFileCount(Integer v) { fileCount = v; }
        public String getPreviewBase64() { return previewBase64; }
        public void setPreviewBase64(String v) { previewBase64 = v; }
    }
}
