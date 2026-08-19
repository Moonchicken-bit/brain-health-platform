package com.brainhealth.auth.security;

import com.brainhealth.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * JWT token provider — thin wrapper over {@link JwtUtil} that packs
 * roles and permissions into access-token claims and exposes typed
 * extractors for downstream filters and resolvers.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = Logger.getLogger(JwtTokenProvider.class.getName());

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";

    /**
     * Generate a signed access token that carries the user's identity,
     * roles, and permissions.
     *
     * @param userId      authenticated user ID
     * @param username    authenticated username
     * @param roles       assigned role names
     * @param permissions assigned permission slugs
     * @return compact JWT access token string
     */
    public String generateAccessToken(Long userId, String username,
                                       List<String> roles,
                                       List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ROLES, roles != null ? roles : Collections.emptyList());
        claims.put(CLAIM_PERMISSIONS, permissions != null ? permissions : Collections.emptyList());
        log.fine(() -> "Generating access token for user " + username);
        return JwtUtil.generateAccessToken(userId, username, claims);
    }

    /**
     * Generate a signed refresh token.
     *
     * @param userId   authenticated user ID
     * @param username authenticated username
     * @return compact JWT refresh token string
     */
    public String generateRefreshToken(Long userId, String username) {
        log.fine(() -> "Generating refresh token for user " + username);
        return JwtUtil.generateRefreshToken(userId, username);
    }

    /**
     * Parse and validate a JWT token.
     *
     * @param token compact JWT string
     * @return parsed {@link Claims}, or {@code null} if the token is
     *         expired, malformed, or otherwise invalid
     */
    public Claims parseToken(String token) {
        return JwtUtil.parseToken(token);
    }

    /**
     * Extract user ID from a raw token string.
     *
     * @param token compact JWT string
     * @return user ID, or {@code null} if the token is invalid
     */
    public Long getUserId(String token) {
        return JwtUtil.getUserId(token);
    }

    /**
     * Extract username from a raw token string.
     *
     * @param token compact JWT string
     * @return username, or {@code null} if the token is invalid
     */
    public String getUsername(String token) {
        return JwtUtil.getUsername(token);
    }

    /**
     * Extract role names from already-parsed claims.
     *
     * @param claims parsed JWT claims
     * @return list of role strings (never {@code null})
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        return Collections.emptyList();
    }

    /**
     * Extract permission slugs from already-parsed claims.
     *
     * @param claims parsed JWT claims
     * @return list of permission strings (never {@code null})
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        Object raw = claims.get(CLAIM_PERMISSIONS);
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        return Collections.emptyList();
    }
}
