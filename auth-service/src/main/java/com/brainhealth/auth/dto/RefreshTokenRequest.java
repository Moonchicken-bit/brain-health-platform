package com.brainhealth.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public class RefreshTokenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public RefreshTokenRequest() {}

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
