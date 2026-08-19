package com.brainhealth.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.springframework.stereotype.Component;
import java.util.logging.Logger;

/**
 * JWT authentication filter — intercepts every request once, extracts and
 * validates the Bearer token, then populates the Spring Security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(JwtAuthenticationFilter.class.getName());

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection — no Lombok.
     *
     * @param jwtTokenProvider JWT token provider for parse / validation
     * @param objectMapper     Jackson ObjectMapper (available as a Spring bean)
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);
            if (token != null) {
                Claims claims = jwtTokenProvider.parseToken(token);
                if (claims != null) {
                    String username = claims.getSubject();
                    List<GrantedAuthority> authorities = buildAuthorities(claims);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    LOGGER.log(Level.FINE, "Authenticated user ''{0}'' with {1} authorities",
                            new Object[]{username, authorities.size()});
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "JWT authentication filter error: {0}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /** Extract the Bearer token from the Authorization header, or null. */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** Build GrantedAuthority list from JWT claims (roles + permissions). */
    @SuppressWarnings("unchecked")
    private List<GrantedAuthority> buildAuthorities(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Roles from the "roles" claim — each gets a ROLE_ prefix
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles != null) {
            for (String role : roles) {
                String roleName = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
                authorities.add(new SimpleGrantedAuthority(roleName));
            }
        }

        // Permissions from the "permissions" claim — used as-is (no prefix)
        List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);
        if (permissions != null) {
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }

        return Collections.unmodifiableList(authorities);
    }
}
