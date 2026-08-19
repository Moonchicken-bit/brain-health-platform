package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.time.LocalDate;

public class SubjectUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String firstName;
    private String lastName;
    private String namePinyin;
    private String sex;
    private LocalDate dateOfBirth;
    private String ethnicity;
    private Integer educationYears;
    private String handedness;
    private String phone;
    private Long maritalStatusCodeId;
    private Long bloodTypeCodeId;
    private Double heightCm;
    private Double weightKg;
    private String addressCity;
    private String addressDistrict;
    private Boolean isConsented;
    private LocalDate consentDate;
    private LocalDate enrollmentDate;
    private String remarks;

    public SubjectUpdateRequest() {}

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getNamePinyin() { return namePinyin; }
    public void setNamePinyin(String namePinyin) { this.namePinyin = namePinyin; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }

    public Integer getEducationYears() { return educationYears; }
    public void setEducationYears(Integer educationYears) { this.educationYears = educationYears; }

    public String getHandedness() { return handedness; }
    public void setHandedness(String handedness) { this.handedness = handedness; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getMaritalStatusCodeId() { return maritalStatusCodeId; }
    public void setMaritalStatusCodeId(Long maritalStatusCodeId) { this.maritalStatusCodeId = maritalStatusCodeId; }

    public Long getBloodTypeCodeId() { return bloodTypeCodeId; }
    public void setBloodTypeCodeId(Long bloodTypeCodeId) { this.bloodTypeCodeId = bloodTypeCodeId; }

    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

    public String getAddressDistrict() { return addressDistrict; }
    public void setAddressDistrict(String addressDistrict) { this.addressDistrict = addressDistrict; }

    public Boolean getIsConsented() { return isConsented; }
    public void setIsConsented(Boolean isConsented) { this.isConsented = isConsented; }

    public LocalDate getConsentDate() { return consentDate; }
    public void setConsentDate(LocalDate consentDate) { this.consentDate = consentDate; }

    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
