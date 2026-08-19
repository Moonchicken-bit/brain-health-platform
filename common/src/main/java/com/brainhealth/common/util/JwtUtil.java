package com.brainhealth.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

/**
 * JWT utility for token generation and validation.
 */
public class JwtUtil {

    private static final Logger log = Logger.getLogger(JwtUtil.class.getName());

    // 64+ byte secret for HMAC-SHA256
    private static final String SECRET = System.getenv().getOrDefault(
        "JWT_SECRET",
        "BrainHealthPlatform-JWT-SecretKey-2024-Must-Be-At-Least-256-Bits-Long!!"
    );
    private static final SecretKey SIGNING_KEY = createSigningKey();

    public static final long ACCESS_TOKEN_EXPIRE_MS = 15 * 60 * 1000L;
    public static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;
    public static final long SERVICE_JOB_TOKEN_EXPIRE_MS = 24 * 60 * 60 * 1000L;

    private static SecretKey createSigningKey() {
        try {
            byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
            // Use SHA-256 to get exactly 256 bits
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(keyBytes);
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (Exception e) {
            // Fallback: try jjwt Keys helper
            try {
                return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.severe("Failed to create signing key: " + ex.getMessage());
                throw new RuntimeException("JWT signing key initialization failed", ex);
            }
        }
    }

    public static String generateAccessToken(Long userId, String username, Map<String, Object> claims) {
        return generateToken(userId, username, claims, ACCESS_TOKEN_EXPIRE_MS);
    }

    /**
     * Creates a server-side token for an already-authorized background job.
     * It preserves the initiating user's roles and scopes, but lasts long
     * enough for large medical archives to finish after the browser token expires.
     */
    public static String generateServiceJobToken(Long userId, String username, Map<String, Object> claims) {
        return generateToken(userId, username, claims, SERVICE_JOB_TOKEN_EXPIRE_MS);
    }

    private static String generateToken(Long userId, String username, Map<String, Object> claims,
                                        long expirationMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .id(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(SIGNING_KEY)
                .compact();
    }

    public static String generateRefreshToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .id(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_MS))
                .signWith(SIGNING_KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(SIGNING_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.fine("Token expired: " + e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warning("Token invalid: " + e.getMessage());
            return null;
        }
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getId());
    }

    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.getSubject();
    }

    public static boolean isExpired(String token) {
        Claims claims = parseToken(token);
        return claims == null;
    }

    public static String sha256Hash(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hash failed", e);
        }
    }
}
