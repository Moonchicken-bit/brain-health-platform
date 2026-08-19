package com.brainhealth.auth.dto;

import java.io.Serializable;

public class UpdateProfileRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String phone;
    private String realName;
    private String department;

    public UpdateProfileRequest() {}

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
