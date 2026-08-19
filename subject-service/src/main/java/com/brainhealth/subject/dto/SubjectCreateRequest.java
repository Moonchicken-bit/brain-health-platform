package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.time.LocalDate;

public class SubjectCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String subjectId;
    private String externalId;
    private String firstName;
    private String lastName;
    private String sex;
    private LocalDate dateOfBirth;
    private String ethnicity;
    private Long ethnicityCodeId;
    private Integer educationYears;
    private Long educationCodeId;
    private String handedness;
    private String namePinyin;
    private Integer ageAtEnrollment;
    private Long maritalStatusCodeId;
    private Long bloodTypeCodeId;
    private String phone;
    private String addressCity;
    private String addressDistrict;
    private Double heightCm;
    private Double weightKg;
    private Long institutionId;
    private Long projectId;
    private LocalDate enrollmentDate;
    private Boolean isConsented;
    private LocalDate consentDate;
    private String remarks;

    public SubjectCreateRequest() {}

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Long getEthnicityCodeId() { return ethnicityCodeId; }
    public void setEthnicityCodeId(Long ethnicityCodeId) { this.ethnicityCodeId = ethnicityCodeId; }

    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }

    public Integer getEducationYears() { return educationYears; }
    public void setEducationYears(Integer educationYears) { this.educationYears = educationYears; }

    public Long getEducationCodeId() { return educationCodeId; }
    public void setEducationCodeId(Long educationCodeId) { this.educationCodeId = educationCodeId; }

    public String getNamePinyin() { return namePinyin; }
    public void setNamePinyin(String namePinyin) { this.namePinyin = namePinyin; }

    public Integer getAgeAtEnrollment() { return ageAtEnrollment; }
    public void setAgeAtEnrollment(Integer ageAtEnrollment) { this.ageAtEnrollment = ageAtEnrollment; }

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

    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long institutionId) { this.institutionId = institutionId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }

    public Boolean getIsConsented() { return isConsented; }
    public void setIsConsented(Boolean isConsented) { this.isConsented = isConsented; }

    public LocalDate getConsentDate() { return consentDate; }
    public void setConsentDate(LocalDate consentDate) { this.consentDate = consentDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
