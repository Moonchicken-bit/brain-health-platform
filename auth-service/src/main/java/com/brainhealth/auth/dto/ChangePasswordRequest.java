package com.brainhealth.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public class ChangePasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Old password is required")
    private String oldPassword;

    @NotBlank(message = "New password is required")
    private String newPassword;

    public ChangePasswordRequest() {}

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
