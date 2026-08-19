package com.brainhealth.subject.controller;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.security.DataScopeGuard;
import com.brainhealth.subject.entity.Session;
import com.brainhealth.subject.repository.SessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class LookupController {
    private final JdbcTemplate jdbc;
    private final SessionRepository sessionRepo;
    private final DataScopeGuard scopeGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LookupController(JdbcTemplate jdbc, SessionRepository sessionRepo, DataScopeGuard scopeGuard) {
        this.jdbc = jdbc;
        this.sessionRepo = sessionRepo;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping("/api/v1/cohorts")
    public ApiResponse<List<Map<String, Object>>> getCohorts() {
        DataScopeGuard.Scope scope = scopeGuard.currentScope();
        if (!scope.admin()) {
            if (scope.projectIds().isEmpty()) return ApiResponse.ok(List.of());
            String placeholders = String.join(",", Collections.nCopies(scope.projectIds().size(), "?"));
            return ApiResponse.ok(jdbc.queryForList(
                    "SELECT id, name, code FROM cohort WHERE project_id IN (" + placeholders + ") ORDER BY id",
                    scope.projectIds().toArray()));
        }
        return ApiResponse.ok(jdbc.queryForList("SELECT id, name, code FROM cohort ORDER BY id"));
    }

    @GetMapping("/api/v1/institutions")
    public ApiResponse<List<Map<String, Object>>> getInstitutions() {
        DataScopeGuard.Scope scope = scopeGuard.currentScope();
        if (!scope.admin()) {
            if (scope.institutionId() == null) return ApiResponse.ok(List.of());
            return ApiResponse.ok(jdbc.queryForList(
                    "SELECT id, name, short_name AS alias FROM institution WHERE id=?", scope.institutionId()));
        }
        return ApiResponse.ok(jdbc.queryForList("SELECT id, name, short_name as alias FROM institution ORDER BY id"));
    }

    @GetMapping("/api/v1/projects")
    public ApiResponse<List<Map<String, Object>>> getProjects() {
        DataScopeGuard.Scope scope = scopeGuard.currentScope();
        if (!scope.admin()) {
            if (scope.projectIds().isEmpty()) return ApiResponse.ok(List.of());
            String placeholders = String.join(",", Collections.nCopies(scope.projectIds().size(), "?"));
            return ApiResponse.ok(jdbc.queryForList(
                    "SELECT id, name, short_name AS alias, principal_investigator AS pi, status " +
                    "FROM project WHERE id IN (" + placeholders + ") ORDER BY id",
                    scope.projectIds().toArray()));
        }
        return ApiResponse.ok(jdbc.queryForList("SELECT id, name, short_name as alias, principal_investigator as pi, status FROM project ORDER BY id"));
    }

    @PostMapping("/api/v1/sessions")
    public ApiResponse<Session> createSession(@RequestBody Map<String, Object> body) {
        scopeGuard.assertAnyRole("ADMIN", "CLINICIAN");
        Long subjectId = requiredLong(body, "subjectId");
        scopeGuard.assertSubjectAccess(subjectId);
        Map<String, Object> subjectScope = jdbc.queryForMap(
                "SELECT COALESCE(s.enrollment_institution_id,s.institution_id) AS institution_id, " +
                "s.project_id AS project_id FROM subject s WHERE s.id=?", subjectId);
        Long projectId = body.get("projectId") != null
                ? Long.valueOf(body.get("projectId").toString())
                : number(subjectScope.get("project_id"));
        if (!scopeGuard.currentScope().admin()) scopeGuard.assertProjectAccess(projectId);
        Long institutionId = body.get("institutionId") != null && scopeGuard.currentScope().admin()
                ? Long.valueOf(body.get("institutionId").toString())
                : number(subjectScope.get("institution_id"));
        if (projectId == null || institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "受试者缺少所属项目或机构，无法创建访视");
        }
        String visitLabel = text(body, "visitLabel", text(body, "visitCode", null));
        if (visitLabel == null || visitLabel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访视标签不能为空");
        }
        String dateText = text(body, "sessionDate", text(body, "visitDate", null));
        if (dateText == null || dateText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访视日期不能为空");
        }
        LocalDate sessionDate;
        try {
            sessionDate = LocalDate.parse(dateText);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访视日期格式应为 YYYY-MM-DD");
        }
        Integer visitNumber = jdbc.queryForObject(
                "SELECT COALESCE(MAX(visit_number),0)+1 FROM session WHERE subject_id=?",
                Integer.class, subjectId);
        Session s = new Session();
        s.setSubjectId(subjectId);
        s.setProjectId(projectId);
        s.setInstitutionId(institutionId);
        s.setVisitLabel(visitLabel.trim());
        s.setVisitNumber(visitNumber);
        s.setSessionDate(sessionDate);
        s.setStatus(text(body, "status", "IN_PROGRESS"));
        s.setRegisteredBy(scopeGuard.currentUsername());
        Session saved = sessionRepo.save(s);
        Map<String, Object> template = resolveTemplate(projectId, visitLabel.trim());
        Long templateVersionId = template == null ? null : number(template.get("version_id"));
        String scaleCodesJson = null;
        String snapshotJson = null;
        Integer deadlineDays = null;
        if (template != null) {
            List<String> scaleCodes = jdbc.queryForList(
                "SELECT scale_code FROM visit_template_scale WHERE template_version_id=? " +
                    "AND patient_visible=1 ORDER BY sort_order,id",
                String.class, templateVersionId);
            scaleCodesJson = writeJson(scaleCodes);
            deadlineDays = template.get("patient_deadline_days") instanceof Number n ? n.intValue() : null;
            Map<String, Object> snapshot = new LinkedHashMap<>(template);
            snapshot.put("scaleCodes", scaleCodes);
            snapshotJson = writeJson(snapshot);
            jdbc.update("UPDATE session SET visit_template_version_id=?,form_snapshot_json=? WHERE id=?",
                templateVersionId, snapshotJson, saved.getId());
        }
        jdbc.update("""
            INSERT INTO assessment_task(subject_id,session_id,visit_code,template_version_id,scale_codes,
                status,assigned_by,assigned_at,due_at)
            VALUES(?,?,?,?,?,'PENDING',?,NOW(),IF(? IS NULL,NULL,DATE_ADD(NOW(),INTERVAL ? DAY)))
            ON DUPLICATE KEY UPDATE visit_code=VALUES(visit_code),assigned_by=VALUES(assigned_by),
                template_version_id=VALUES(template_version_id),scale_codes=VALUES(scale_codes),
                due_at=VALUES(due_at)
            """, subjectId, saved.getId(), visitLabel.trim(), templateVersionId, scaleCodesJson,
            scopeGuard.currentUsername(), deadlineDays, deadlineDays);
        return ApiResponse.created(saved);
    }

    private Map<String, Object> resolveTemplate(Long projectId, String visitCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT v.id AS version_id,t.id AS template_id,t.code AS template_code,t.name AS template_name,
                   v.version_no,v.visit_code,v.visit_name,v.allow_unified_upload,
                   v.required_modules,v.patient_deadline_days
            FROM visit_template t
            JOIN visit_template_version v ON v.id=t.active_version_id
            WHERE t.status='ACTIVE' AND v.status='PUBLISHED' AND v.visit_code=?
              AND (t.project_id=? OR t.project_id IS NULL)
            ORDER BY (t.project_id IS NOT NULL) DESC,t.id DESC
            LIMIT 1
            """, visitCode, projectId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("访视模板快照保存失败", ex); }
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Long requiredLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + "不能为空");
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + "格式错误");
        }
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null ? fallback : value.toString();
    }
}
