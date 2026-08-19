package com.brainhealth.adni.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "adni_subject")
public class AdniSubject extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "adni_subject_id", nullable = false, unique = true, length = 50)
    private String adniSubjectId;
    @Column(length = 50)
    private String diagnosis;
    @Column(length = 2)
    private String sex;
    private Integer age;
    @Column(name = "education_years")
    private Integer educationYears;
    @Column(name = "apoe_genotype", length = 10)
    private String apoeGenotype;
    @Column(name = "has_imaging")
    private Boolean hasImaging = false;
    @Column(name = "has_genetics")
    private Boolean hasGenetics = false;
    @Column(name = "local_subject_id")
    private Long localSubjectId;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getAdniSubjectId() { return adniSubjectId; } public void setAdniSubjectId(String v) { adniSubjectId = v; }
    public String getDiagnosis() { return diagnosis; } public void setDiagnosis(String v) { diagnosis = v; }
    public String getSex() { return sex; } public void setSex(String v) { sex = v; }
    public Integer getAge() { return age; } public void setAge(Integer v) { age = v; }
    public Integer getEducationYears() { return educationYears; } public void setEducationYears(Integer v) { educationYears = v; }
    public String getApoeGenotype() { return apoeGenotype; } public void setApoeGenotype(String v) { apoeGenotype = v; }
    public Boolean getHasImaging() { return hasImaging; } public void setHasImaging(Boolean v) { hasImaging = v; }
    public Boolean getHasGenetics() { return hasGenetics; } public void setHasGenetics(Boolean v) { hasGenetics = v; }
    public Long getLocalSubjectId() { return localSubjectId; } public void setLocalSubjectId(Long v) { localSubjectId = v; }
}
