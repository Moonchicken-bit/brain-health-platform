package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SubjectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String subjectId;
    private String externalId;
    private String firstName;
    private String lastName;
    private String namePinyin;
    private Long institutionId;
    private Long projectId;
    private String sex;
    private LocalDate dateOfBirth;
    private Integer ageAtEnrollment;
    private String ethnicity;
    private Long ethnicityCodeId;
    private Long educationCodeId;
    private Integer educationYears;
    private String handedness;
    private Long maritalStatusCodeId;
    private Long bloodTypeCodeId;
    private String phone;
    private String addressCity;
    private String addressDistrict;
    private Double heightCm;
    private Double weightKg;
    private Double bmi;
    private LocalDate enrollmentDate;
    private Long enrollmentInstitutionId;
    private String status;
    private Boolean isConsented;
    private LocalDate consentDate;
    private String remarks;
    private Boolean isActive;

    // Display names (resolved from FK lookups)
    private String institutionName;
    private String projectName;
    private String sexName;
    private String maritalStatusName;
    private String bloodTypeName;
    private String nationalityName;
    private String insuranceTypeName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SubjectDTO() {}

    // ---- Getters / Setters ----
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

    public Long getEthnicityCodeId() { return ethnicityCodeId; }
    public void setEthnicityCodeId(Long ethnicityCodeId) { this.ethnicityCodeId = ethnicityCodeId; }

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

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String v) { this.institutionName = v; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }

    public String getSexName() { return sexName; }
    public void setSexName(String v) { this.sexName = v; }

    public String getMaritalStatusName() { return maritalStatusName; }
    public void setMaritalStatusName(String v) { this.maritalStatusName = v; }

    public String getBloodTypeName() { return bloodTypeName; }
    public void setBloodTypeName(String v) { this.bloodTypeName = v; }

    public String getNationalityName() { return nationalityName; }
    public void setNationalityName(String v) { this.nationalityName = v; }

    public String getInsuranceTypeName() { return insuranceTypeName; }
    public void setInsuranceTypeName(String v) { this.insuranceTypeName = v; }
}
