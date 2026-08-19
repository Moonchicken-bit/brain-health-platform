package com.brainhealth.imaging.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "imaging_session")
public class ImagingSession extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "scanner_id")
    private Long scannerId;
    @Column(name = "modality_id", nullable = false)
    private Long modalityId;
    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;
    @Column(name = "series_count")
    private Integer seriesCount = 0;
    @Column(name = "qc_status", length = 30)
    private String qcStatus = "Pending";
    @Column(name = "preprocessing_status", length = 30)
    private String preprocessingStatus = "None";
    @Column(columnDefinition = "TEXT")
    private String notes;
    // getters/setters
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public Long getScannerId() { return scannerId; }
    public void setScannerId(Long v) { scannerId = v; }
    public Long getModalityId() { return modalityId; }
    public void setModalityId(Long v) { modalityId = v; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate v) { acquisitionDate = v; }
    public Integer getSeriesCount() { return seriesCount; }
    public void setSeriesCount(Integer v) { seriesCount = v; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String v) { qcStatus = v; }
    public String getPreprocessingStatus() { return preprocessingStatus; }
    public void setPreprocessingStatus(String v) { preprocessingStatus = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}
