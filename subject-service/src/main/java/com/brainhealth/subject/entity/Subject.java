package com.brainhealth.subject.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "subject")
public class Subject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Keep old field name for backward compatibility, map to subject_code column
    @Column(name = "subject_code", nullable = false, unique = true)
    private String subjectId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "name_pinyin")
    private String namePinyin;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "sex")
    private String sex;

    // Keep old field name for backward compatibility, map to birth_date column
    @Column(name = "birth_date")
    private LocalDate dateOfBirth;

    @Column(name = "age_at_enrollment")
    private Integer ageAtEnrollment;

    @Column(name = "ethnicity")
    private String ethnicity;

    @Column(name = "nation_code_id")
    private Long nationCodeId;

    @Column(name = "education_code_id")
    private Long educationCodeId;

    @Column(name = "nationality_code_id")
    private Long nationalityCodeId;

    @Column(name = "insurance_type_code_id")
    private Long insuranceTypeCodeId;

    @Column(name = "id_card_hash")
    private String idCardHash;

    @Column(name = "education_years")
    private Integer educationYears;

    @Column(name = "handedness")
    private String handedness;

    @Column(name = "marital_status_code_id")
    private Long maritalStatusCodeId;

    @Column(name = "blood_type_code_id")
    private Long bloodTypeCodeId;

    @Column(name = "phone_hash")
    private String phoneHash;

    @Column(name = "address_city")
    private String addressCity;

    @Column(name = "address_district")
    private String addressDistrict;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "bmi")
    private Double bmi;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "enrollment_institution_id")
    private Long enrollmentInstitutionId;

    @Column(name = "status")
    private String status = "screening";

    @Column(name = "is_consented")
    private Boolean isConsented = false;

    @Column(name = "consent_date")
    private LocalDate consentDate;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    // ---- constructors ----

    public Subject() {
    }

    // ---- getters and setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getNamePinyin() { return namePinyin; }
    public void setNamePinyin(String namePinyin) { this.namePinyin = namePinyin; }

    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long institutionId) { this.institutionId = institutionId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Integer getAgeAtEnrollment() { return ageAtEnrollment; }
    public void setAgeAtEnrollment(Integer ageAtEnrollment) { this.ageAtEnrollment = ageAtEnrollment; }

    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }

    public Long getNationCodeId() { return nationCodeId; }
    public void setNationCodeId(Long nationCodeId) { this.nationCodeId = nationCodeId; }

    public Long getEducationCodeId() { return educationCodeId; }
    public void setEducationCodeId(Long educationCodeId) { this.educationCodeId = educationCodeId; }

    public Integer getEducationYears() { return educationYears; }
    public void setEducationYears(Integer educationYears) { this.educationYears = educationYears; }

    public String getHandedness() { return handedness; }
    public void setHandedness(String handedness) { this.handedness = handedness; }

    public Long getMaritalStatusCodeId() { return maritalStatusCodeId; }
    public void setMaritalStatusCodeId(Long maritalStatusCodeId) { this.maritalStatusCodeId = maritalStatusCodeId; }

    public Long getBloodTypeCodeId() { return bloodTypeCodeId; }
    public void setBloodTypeCodeId(Long bloodTypeCodeId) { this.bloodTypeCodeId = bloodTypeCodeId; }

    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }

    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

    public String getAddressDistrict() { return addressDistrict; }
    public void setAddressDistrict(String addressDistrict) { this.addressDistrict = addressDistrict; }

    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public Double getBmi() { return bmi; }
    public void setBmi(Double bmi) { this.bmi = bmi; }

    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }

    public Long getEnrollmentInstitutionId() { return enrollmentInstitutionId; }
    public void setEnrollmentInstitutionId(Long enrollmentInstitutionId) { this.enrollmentInstitutionId = enrollmentInstitutionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsConsented() { return isConsented; }
    public void setIsConsented(Boolean isConsented) { this.isConsented = isConsented; }

    public LocalDate getConsentDate() { return consentDate; }
    public void setConsentDate(LocalDate consentDate) { this.consentDate = consentDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Long getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(Long registeredBy) { this.registeredBy = registeredBy; }
}
