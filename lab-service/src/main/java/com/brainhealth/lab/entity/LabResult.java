package com.brainhealth.lab.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "lab_result")
public class LabResult extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "lab_test_id", nullable = false)
    private Long labTestId;
    @Column(name = "result", length = 500)
    private String result;
    @Column(length = 50)
    private String unit;
    @Column(name = "reference_range", length = 200)
    private String referenceRange;
    @Column(name = "is_abnormal")
    private Boolean isAbnormal = false;
    @Column(name = "collection_date")
    private LocalDate collectionDate;
    @Column(name = "technician_id")
    private Long technicianId;
    @Column(columnDefinition = "TEXT")
    private String notes;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public Long getLabTestId() { return labTestId; }
    public void setLabTestId(Long v) { labTestId = v; }
    public String getResult() { return result; }
    public void setResult(String v) { result = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { unit = v; }
    public String getReferenceRange() { return referenceRange; }
    public void setReferenceRange(String v) { referenceRange = v; }
    public Boolean getIsAbnormal() { return isAbnormal; }
    public void setIsAbnormal(Boolean v) { isAbnormal = v; }
    public LocalDate getCollectionDate() { return collectionDate; }
    public void setCollectionDate(LocalDate v) { collectionDate = v; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long v) { technicianId = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}
