package com.brainhealth.auth.service;

import com.brainhealth.auth.dto.ChangePasswordRequest;
import com.brainhealth.auth.dto.ForgotPasswordRequest;
import com.brainhealth.auth.dto.LoginRequest;
import com.brainhealth.auth.dto.LoginResponse;
import com.brainhealth.auth.dto.UserProfileResponse;
import com.brainhealth.auth.entity.Role;
import com.brainhealth.auth.entity.User;
import com.brainhealth.auth.entity.UserRole;
import com.brainhealth.auth.repository.RoleRepository;
import com.brainhealth.auth.repository.UserRepository;
import com.brainhealth.auth.repository.UserRoleRepository;
import com.brainhealth.common.constant.Constants;
import com.brainhealth.common.exception.BusinessException;
import com.brainhealth.common.exception.ErrorCode;
import com.brainhealth.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Authentication and authorization service.
 * Handles login, logout, token refresh, and password management.
 */
@Service
public class AuthService {

    private static final Logger log = Logger.getLogger(AuthService.class.getName());

    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = JwtUtil.ACCESS_TOKEN_EXPIRE_MS / 1000L;
    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = JwtUtil.REFRESH_TOKEN_EXPIRE_MS / 1000L;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;
    private final String resetMailFrom;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TotpService totpService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate stringRedisTemplate,
                       JdbcTemplate jdbcTemplate,
                       JavaMailSender mailSender,
                       TotpService totpService,
                       @Value("${brain-health.auth.reset-from:no-reply@brainhealth.local}") String resetMailFrom) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.totpService = totpService;
        this.resetMailFrom = resetMailFrom;
    }

    /**
     * Authenticate a user with username and password, returning JWT tokens.
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        log.log(Level.INFO, "Login attempt for user: {0}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "Login failed: user not found - {0}", username);
                    return new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
                });

        if (user.getIsActive() == null || !user.getIsActive()) {
            log.log(Level.WARNING, "Login failed: account disabled - {0}", username);
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        if (user.getIsLocked() != null && user.getIsLocked()) {
            log.log(Level.WARNING, "Login failed: account locked - {0}", username);
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.log(Level.WARNING, "Login failed: wrong password - {0}", username);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (StringUtils.hasText(user.getOtpSecret()) && !verifySecondFactor(user, request.getOtpCode())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Two-factor verification code is required or invalid");
        }

        String accessToken = JwtUtil.generateAccessToken(
            user.getId(), user.getUsername(), accessClaims(user));
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        cacheRefreshToken(user.getId(), refreshToken);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.log(Level.INFO, "Login successful for user: {0}", username);

        return new LoginResponse(accessToken, refreshToken, ACCESS_TOKEN_EXPIRE_SECONDS, TOKEN_TYPE_BEARER);
    }

    /**
     * Logout by blacklisting the access token in Redis and removing the refresh token.
     */
    public void logout(String bearerToken) {
        String token = extractBearerToken(bearerToken);
        if (!StringUtils.hasText(token)) {
            log.warning("Logout called with empty or invalid token");
            return;
        }

        Claims claims = JwtUtil.parseToken(token);
        if (claims == null) {
            log.warning("Logout called with unparseable token, blacklisting anyway");
            blacklistToken(token, JwtUtil.ACCESS_TOKEN_EXPIRE_MS, TimeUnit.MILLISECONDS);
            return;
        }

        Long userId = Long.valueOf(claims.getId());
        long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingTtl <= 0) {
            log.fine("Token already expired, nothing to blacklist");
            removeRefreshToken(userId);
            return;
        }

        blacklistToken(token, remainingTtl, TimeUnit.MILLISECONDS);
        removeRefreshToken(userId);

        log.log(Level.INFO, "User {0} logged out, token blacklisted", userId);
    }

    /**
     * Refresh access and refresh tokens using a valid refresh token.
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Refresh token is empty");
        }

        Claims claims = JwtUtil.parseToken(refreshToken);
        if (claims == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenType = claims.get("type", String.class);
        if (!"refresh".equals(tokenType)) {
            log.warning("Token is not a refresh token");
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Not a refresh token");
        }

        Long userId = Long.valueOf(claims.getId());

        if (isRefreshTokenRevoked(userId, refreshToken)) {
            log.log(Level.WARNING, "Refresh token has been revoked for user: {0}", userId);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Refresh token revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "User not found during token refresh: {0}", userId);
                    return new BusinessException(ErrorCode.UNAUTHORIZED);
                });

        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        if (user.getIsLocked() != null && user.getIsLocked()) {
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        String newAccessToken = JwtUtil.generateAccessToken(
            user.getId(), user.getUsername(), accessClaims(user));
        String newRefreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        removeRefreshToken(userId);
        cacheRefreshToken(userId, newRefreshToken);

        log.log(Level.INFO, "Tokens refreshed for user: {0}", user.getUsername());

        return new LoginResponse(newAccessToken, newRefreshToken, ACCESS_TOKEN_EXPIRE_SECONDS, TOKEN_TYPE_BEARER);
    }

    /**
     * Get the currently authenticated user's profile including roles and permissions.
     */
    public UserProfileResponse getCurrentUser(String authHeader) {
        String username = extractUsernameFromHeader(authHeader);
        if (username == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "Authenticated user not found in database: {0}", username);
                    return new BusinessException(ErrorCode.UNAUTHORIZED);
                });

        List<String> roleNames = loadRoleNames(user.getId());

        UserProfileResponse.UserInfoDTO userInfo = new UserProfileResponse.UserInfoDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setInstitutionId(user.getInstitutionId());
        userInfo.setSubjectId(loadBoundSubjectId(user.getId()));
        userInfo.setDepartment(user.getDepartment());
        userInfo.setRoles(roleNames);

        UserProfileResponse profile = new UserProfileResponse();
        profile.setUser(userInfo);
        profile.setPermissions(loadPermissions(user.getId()));

        return profile;
    }

    /**
     * Change the current user's password after verifying the old password.
     */
    public void changePassword(ChangePasswordRequest request) {
        validatePasswordStrength(request.getNewPassword());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            log.log(Level.WARNING, "Change password failed: old password mismatch for user {0}", username);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR, "Old password is incorrect");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "New password must differ from old password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.log(Level.INFO, "Password changed successfully for user: {0}", username);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String rateKey = "auth:password-reset:rate:" + email;
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                rateKey, "1", 60, TimeUnit.SECONDS))) return;
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(resetCodeKey(email),
            passwordEncoder.encode(code), 10, TimeUnit.MINUTES);
        stringRedisTemplate.delete(resetAttemptsKey(email));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resetMailFrom);
        message.setTo(email);
        message.setSubject("Brain Health password reset code");
        message.setText("Your password reset code is: " + code + "\nIt expires in 10 minutes.");
        try {
            mailSender.send(message);
        } catch (RuntimeException mailFailure) {
            log.log(Level.SEVERE, "Password reset email delivery failed", mailFailure);
        }
    }

    public void resetPasswordSecure(String email, String code, String newPassword) {
        validatePasswordStrength(newPassword);
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        String attemptsKey = resetAttemptsKey(normalized);
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) stringRedisTemplate.expire(attemptsKey, 10, TimeUnit.MINUTES);
        if (attempts != null && attempts > 5) throw new BusinessException(ErrorCode.BAD_REQUEST, "Too many attempts");
        String storedHash = stringRedisTemplate.opsForValue().get(resetCodeKey(normalized));
        if (storedHash == null || !passwordEncoder.matches(code, storedHash)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid or expired verification code");
        }
        User user = userRepository.findByEmail(normalized)
            .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Invalid or expired verification code"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        stringRedisTemplate.delete(List.of(resetCodeKey(normalized), attemptsKey,
            Constants.REDIS_REFRESH_TOKEN + user.getId()));
    }

    private static String resetCodeKey(String email) { return "auth:password-reset:code:" + email; }
    private static String resetAttemptsKey(String email) { return "auth:password-reset:attempts:" + email; }

    public Map<String, Object> setupTwoFactor() {
        User user = currentAuthenticatedUser();
        String secret = totpService.generateSecret();
        List<String> recoveryCodes = totpService.recoveryCodes();
        stringRedisTemplate.opsForValue().set("auth:2fa:pending:" + user.getId(),
            totpService.encrypt(secret + "|" + String.join(",", recoveryCodes)), 10, TimeUnit.MINUTES);
        return Map.of("secret", secret,
            "otpauthUri", "otpauth://totp/BrainHealth:" + user.getUsername() +
                "?secret=" + secret + "&issuer=BrainHealth&digits=6&period=30",
            "recoveryCodes", recoveryCodes);
    }

    public void verifyAndEnableTwoFactor(String code) {
        User user = currentAuthenticatedUser();
        String pending = stringRedisTemplate.opsForValue().get("auth:2fa:pending:" + user.getId());
        if (!StringUtils.hasText(pending)) throw new BusinessException(ErrorCode.BAD_REQUEST, "2FA setup expired");
        String[] parts = totpService.decrypt(pending).split("\\|", 2);
        if (!totpService.verify(parts[0], code)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid verification code");
        user.setOtpSecret(totpService.encrypt(parts[0]));
        List<String> hashes = Arrays.stream(parts[1].split(",")).map(passwordEncoder::encode).toList();
        user.setOtpRecoveryCodes(String.join(",", hashes));
        userRepository.save(user);
        stringRedisTemplate.delete("auth:2fa:pending:" + user.getId());
    }

    private boolean verifySecondFactor(User user, String code) {
        if (!StringUtils.hasText(code)) return false;
        if (totpService.verify(totpService.decrypt(user.getOtpSecret()), code)) return true;
        if (!StringUtils.hasText(user.getOtpRecoveryCodes())) return false;
        List<String> hashes = new java.util.ArrayList<>(Arrays.asList(user.getOtpRecoveryCodes().split(",")));
        for (int i = 0; i < hashes.size(); i++) {
            if (passwordEncoder.matches(code.trim().toUpperCase(java.util.Locale.ROOT), hashes.get(i))) {
                hashes.remove(i);
                user.setOtpRecoveryCodes(String.join(",", hashes));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    private User currentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8 || password.length() > 128
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "密码必须为 8–128 位，并同时包含字母和数字");
        }
    }

    // ---- Private helpers ----

    /**
     * Strip the Bearer prefix from an Authorization header value.
     */
    private String extractBearerToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }
        if (bearerToken.startsWith(Constants.HEADER_BEARER_PREFIX)) {
            return bearerToken.substring(Constants.HEADER_BEARER_PREFIX.length());
        }
        return bearerToken;
    }

    /**
     * Blacklist a token in Redis with the given TTL so it cannot be reused.
     */
    private void blacklistToken(String token, long ttl, TimeUnit unit) {
        String key = Constants.REDIS_TOKEN_BLACKLIST + token;
        stringRedisTemplate.opsForValue().set(key, "1", ttl, unit);
        log.log(Level.FINE, "Token blacklisted with key: {0}", key);
    }

    /**
     * Store the active refresh token for a user in Redis.
     */
    private void cacheRefreshToken(Long userId, String refreshToken) {
        String key = Constants.REDIS_REFRESH_TOKEN + userId;
        stringRedisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Remove the cached refresh token for a user from Redis.
     */
    private void removeRefreshToken(Long userId) {
        String key = Constants.REDIS_REFRESH_TOKEN + userId;
        stringRedisTemplate.delete(key);
    }

    /**
     * Check whether a refresh token has been revoked by comparing it against the cached value.
     */
    private boolean isRefreshTokenRevoked(Long userId, String refreshToken) {
        String key = Constants.REDIS_REFRESH_TOKEN + userId;
        String stored = stringRedisTemplate.opsForValue().get(key);
        return stored == null || !stored.equals(refreshToken);
    }

    /**
     * Load the role names assigned to a user.
     */
    private String extractUsernameFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        Claims claims = JwtUtil.parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    private List<String> loadRoleNames(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        List<Role> roles = roleRepository.findAllById(roleIds);
        return roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
    }

    private List<String> loadPermissions(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT p.code FROM permission p " +
                "JOIN role_permission rp ON rp.permission_id=p.id " +
                "JOIN user_role ur ON ur.role_id=rp.role_id " +
                "WHERE ur.user_id=? ORDER BY p.code",
                String.class, userId);
    }

    private Map<String, Object> accessClaims(User user) {
        List<UserRole> assignments = userRoleRepository.findByUserId(user.getId());
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", loadRoleNames(user.getId()));
        claims.put("permissions", loadPermissions(user.getId()));
        claims.put("institutionId", user.getInstitutionId());
        claims.put("subjectId", loadBoundSubjectId(user.getId()));
        claims.put("projectIds", assignments.stream()
            .map(UserRole::getProjectId).filter(Objects::nonNull).distinct().toList());
        return claims;
    }

    private Long loadBoundSubjectId(Long userId) {
        List<Long> values = jdbcTemplate.queryForList(
                "SELECT subject_id FROM user_subject_binding WHERE user_id=?",
                Long.class, userId);
        return values.isEmpty() ? null : values.get(0);
    }
}
