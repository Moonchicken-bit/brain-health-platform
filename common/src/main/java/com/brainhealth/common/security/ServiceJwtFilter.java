package com.brainhealth.common.security;

import com.brainhealth.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates JWTs again at each servlet microservice boundary and replaces
 * identity/scope headers with signed claim values. This prevents callers from
 * bypassing the gateway and spoofing X-User-* headers on a service port.
 */
@Component
public class ServiceJwtFilter extends OncePerRequestFilter {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password", "/actuator/health", "/actuator/info");
    private static final List<String> TRUSTED_HEADERS = List.of(
            "x-user-id", "x-username", "x-institution-id", "x-project-ids", "x-roles");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || PUBLIC_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(new TrustedHeaderRequest(request, Map.of()), response);
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "请先登录");
            return;
        }
        Claims claims = JwtUtil.parseToken(authorization.substring(7));
        if (claims == null || "refresh".equals(claims.get("type", String.class))) {
            unauthorized(response, "登录状态无效或已过期");
            return;
        }
        if (request.getRequestURI().startsWith("/api/v1/admin")
                && !hasAdminRole(claims.get("roles"))) {
            forbidden(response, "仅系统管理员可执行此操作");
            return;
        }
        Map<String, String> trusted = new LinkedHashMap<>();
        trusted.put("x-user-id", claims.getId());
        trusted.put("x-username", claims.getSubject());
        Object institution = claims.get("institutionId");
        if (institution != null) trusted.put("x-institution-id", institution.toString());
        Object subject = claims.get("subjectId");
        if (subject != null) trusted.put("x-subject-id", subject.toString());
        putListClaim(trusted, "x-project-ids", claims.get("projectIds"));
        putListClaim(trusted, "x-roles", claims.get("roles"));
        filterChain.doFilter(new TrustedHeaderRequest(request, trusted), response);
    }

    private static void putListClaim(Map<String, String> headers, String name, Object value) {
        if (value instanceof List<?> list) {
            headers.put(name, String.join(",", list.stream().map(Object::toString).toList()));
        }
    }

    private static boolean hasAdminRole(Object value) {
        return value instanceof List<?> list && list.stream().map(Object::toString)
                .map(String::toUpperCase)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ADMIN".equals(role));
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private static void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }

    private static final class TrustedHeaderRequest extends HttpServletRequestWrapper {
        private final Map<String, String> trusted;

        private TrustedHeaderRequest(HttpServletRequest request, Map<String, String> trusted) {
            super(request);
            this.trusted = trusted;
        }

        @Override
        public String getHeader(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            return TRUSTED_HEADERS.contains(normalized) ? trusted.get(normalized) : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!TRUSTED_HEADERS.contains(normalized)) return super.getHeaders(name);
            String value = trusted.get(normalized);
            return value == null ? Collections.emptyEnumeration()
                    : Collections.enumeration(List.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !TRUSTED_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            names.addAll(trusted.keySet());
            return Collections.enumeration(names);
        }
    }
}
