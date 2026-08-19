package com.brainhealth.genetics.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "genetics_sample")
public class GeneticsSample extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "sample_type", length = 50)
    private String sampleType = "Blood";
    @Column(name = "platform", length = 100)
    private String platform;
    @Column(name = "reference_genome", length = 50)
    private String referenceGenome = "hg38";
    @Column(name = "qc_status", length = 30)
    private String qcStatus = "Pending";
    @Column(name = "variant_count")
    private Integer variantCount = 0;
    @Column(name = "vcf_file_name", length = 500)
    private String vcfFileName;
    @Column(name = "vcf_file_path", length = 500)
    private String vcfFilePath;
    @Column(columnDefinition = "TEXT")
    private String notes;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public String getSampleType() { return sampleType; }
    public void setSampleType(String v) { sampleType = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { platform = v; }
    public String getReferenceGenome() { return referenceGenome; }
    public void setReferenceGenome(String v) { referenceGenome = v; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String v) { qcStatus = v; }
    public Integer getVariantCount() { return variantCount; }
    public void setVariantCount(Integer v) { variantCount = v; }
    public String getVcfFileName() { return vcfFileName; }
    public void setVcfFileName(String v) { vcfFileName = v; }
    public String getVcfFilePath() { return vcfFilePath; }
    public void setVcfFilePath(String v) { vcfFilePath = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}
