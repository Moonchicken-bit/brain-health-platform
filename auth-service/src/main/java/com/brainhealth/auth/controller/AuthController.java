package com.brainhealth.auth.controller;

import com.brainhealth.auth.dto.*;
import com.brainhealth.auth.service.AuthService;
import com.brainhealth.common.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ApiResponse.ok(null, "已退出登录");
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.ok(authService.getCurrentUser(authHeader));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok(null, "密码修改成功");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok(null, "如果该邮箱已注册，验证码将发送至邮箱");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPasswordSecure(request.getEmail(), request.getCode(), request.getNewPassword());
        return ApiResponse.ok(null, "密码重置成功");
    }

    @PostMapping("/2fa/setup")
    public ApiResponse<java.util.Map<String, Object>> setupTwoFactor() {
        return ApiResponse.ok(authService.setupTwoFactor());
    }

    @PostMapping("/2fa/verify")
    public ApiResponse<Void> verifyTwoFactor(@RequestBody java.util.Map<String, String> body) {
        authService.verifyAndEnableTwoFactor(body.get("code"));
        return ApiResponse.ok(null, "双因素认证已启用");
    }
}
