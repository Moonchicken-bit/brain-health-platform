package com.brainhealth.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
public class DataScopeGuard {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public DataScopeGuard(JdbcTemplate jdbc, ObjectProvider<HttpServletRequest> requestProvider) {
        this.jdbc = jdbc;
        this.requestProvider = requestProvider;
    }

    public void assertSubjectAccess(Long subjectId) {
        if (subjectId == null) throw forbidden();
        Scope scope = currentScope();
        if (scope.admin()) return;
        if (scope.roles().stream().anyMatch(role -> role.endsWith("PATIENT"))) {
            if (Objects.equals(scope.subjectId(), subjectId)) return;
            throw forbidden();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT COALESCE(s.enrollment_institution_id,s.institution_id) institution_id, "
                + "s.project_id project_id FROM subject s WHERE s.id=?", subjectId);
        if (rows.isEmpty() || rows.stream().noneMatch(row -> scope.allows(row.get("institution_id"), row.get("project_id")))) {
            throw forbidden();
        }
    }

    public void assertSessionAccess(Long sessionId) {
        if (sessionId == null) throw forbidden();
        Scope scope = currentScope();
        if (scope.admin()) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.institution_id, s.project_id, s.subject_id FROM `session` s WHERE s.id=?", sessionId);
        if (rows.isEmpty()) throw forbidden();
        Map<String, Object> row = rows.get(0);
        if (!scope.allows(row.get("institution_id"), row.get("project_id"))) {
            assertSubjectAccess(((Number) row.get("subject_id")).longValue());
        }
    }

    public void assertProjectAccess(Long projectId) {
        Scope scope = currentScope();
        if (!scope.admin() && (projectId == null || !scope.projectIds().contains(projectId))) {
            throw forbidden();
        }
    }

    public void assertAnyRole(String... allowedRoles) {
        Scope scope = currentScope();
        if (scope.admin()) return;
        Set<String> allowed = new HashSet<>();
        for (String role : allowedRoles) {
            if (role != null) allowed.add(role.toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""));
        }
        boolean permitted = scope.roles().stream()
                .map(role -> role.replaceFirst("^ROLE_", ""))
                .anyMatch(allowed::contains);
        if (!permitted) throw forbidden();
    }

    public String currentUsername() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        return request == null ? null : request.getHeader("X-Username");
    }

    public Long currentSubjectId() {
        return currentScope().subjectId();
    }

    public List<Long> accessibleSubjectIds() {
        Scope scope = currentScope();
        if (scope.admin()) {
            return jdbc.queryForList("SELECT id FROM subject WHERE is_active=1", Long.class);
        }
        if (scope.roles().stream().anyMatch(role -> role.endsWith("PATIENT"))) {
            return scope.subjectId() == null ? List.of() : List.of(scope.subjectId());
        }
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (scope.institutionId() != null) {
            conditions.add("COALESCE(enrollment_institution_id,institution_id)=?");
            arguments.add(scope.institutionId());
        }
        if (!scope.projectIds().isEmpty()) {
            conditions.add("project_id IN (" +
                String.join(",", Collections.nCopies(scope.projectIds().size(), "?")) + ")");
            arguments.addAll(scope.projectIds());
        }
        if (conditions.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT id FROM subject WHERE is_active=1 AND (" +
            String.join(" OR ", conditions) + ")", Long.class, arguments.toArray());
    }

    public Scope currentScope() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) throw forbidden();
        Set<String> roles = new LinkedHashSet<>();
        split(request.getHeader("X-Roles")).forEach(role -> roles.add(role.toUpperCase(Locale.ROOT)));
        Long institutionId = parseLong(request.getHeader("X-Institution-Id"));
        Set<Long> projects = new LinkedHashSet<>();
        for (String value : split(request.getHeader("X-Project-Ids"))) {
            Long id = parseLong(value);
            if (id != null) projects.add(id);
        }
        Long subjectId = parseLong(request.getHeader("X-Subject-Id"));
        return new Scope(roles.contains("ROLE_ADMIN") || roles.contains("ADMIN"), institutionId, projects, roles, subjectId);
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) if (!item.isBlank()) result.add(item.trim());
        return result;
    }

    private static Long parseLong(String value) {
        try { return value == null || value.isBlank() ? null : Long.valueOf(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该机构或项目的数据");
    }

    public record Scope(boolean admin, Long institutionId, Set<Long> projectIds, Set<String> roles, Long subjectId) {
        public Scope(boolean admin, Long institutionId, Set<Long> projectIds) {
            this(admin, institutionId, projectIds, admin ? Set.of("ADMIN") : Set.of(), null);
        }

        boolean allows(Object institution, Object project) {
            Long institutionValue = institution instanceof Number n ? n.longValue() : null;
            Long projectValue = project instanceof Number n ? n.longValue() : null;
            return institutionId != null && Objects.equals(institutionId, institutionValue)
                || projectValue != null && projectIds.contains(projectValue);
        }
    }
}
