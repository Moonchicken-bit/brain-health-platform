package com.brainhealth.imaging.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "imaging_series")
public class ImagingSeries extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "imaging_session_id", nullable = false)
    private Long imagingSessionId;
    @Column(name = "series_uid", length = 200)
    private String seriesUid;
    @Column(name = "series_number")
    private Integer seriesNumber;
    @Column(name = "series_description", length = 500)
    private String seriesDescription;
    @Column(name = "sequence_name", length = 200)
    private String sequenceName;
    @Column(name = "echo_time")
    private Double echoTime;
    @Column(name = "repetition_time")
    private Double repetitionTime;
    @Column(name = "slice_thickness")
    private Double sliceThickness;
    @Column(name = "number_of_files")
    private Integer numberOfFiles = 0;
    @Column(name = "file_type", length = 50)
    private String fileType = "DICOM";
    @Column(name = "file_path", length = 500)
    private String filePath;
    @Column(name = "qc_status", length = 30)
    private String qcStatus = "Pending";
    @Column(columnDefinition = "TEXT")
    private String qcNotes;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getImagingSessionId() { return imagingSessionId; }
    public void setImagingSessionId(Long v) { imagingSessionId = v; }
    public String getSeriesUid() { return seriesUid; }
    public void setSeriesUid(String v) { seriesUid = v; }
    public Integer getSeriesNumber() { return seriesNumber; }
    public void setSeriesNumber(Integer v) { seriesNumber = v; }
    public String getSeriesDescription() { return seriesDescription; }
    public void setSeriesDescription(String v) { seriesDescription = v; }
    public String getSequenceName() { return sequenceName; }
    public void setSequenceName(String v) { sequenceName = v; }
    public Double getEchoTime() { return echoTime; }
    public void setEchoTime(Double v) { echoTime = v; }
    public Double getRepetitionTime() { return repetitionTime; }
    public void setRepetitionTime(Double v) { repetitionTime = v; }
    public Double getSliceThickness() { return sliceThickness; }
    public void setSliceThickness(Double v) { sliceThickness = v; }
    public Integer getNumberOfFiles() { return numberOfFiles; }
    public void setNumberOfFiles(Integer v) { numberOfFiles = v; }
    public String getFileType() { return fileType; }
    public void setFileType(String v) { fileType = v; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String v) { filePath = v; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String v) { qcStatus = v; }
    public String getQcNotes() { return qcNotes; }
    public void setQcNotes(String v) { qcNotes = v; }
}
