package com.brainhealth.subject.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final JdbcTemplate jdbc;
    private final DataScopeGuard scopeGuard;

    public SessionController(JdbcTemplate jdbc, DataScopeGuard scopeGuard) {
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
    }

    /**
     * GET /api/v1/sessions
     * Paged list, optional filters: subjectId, visitCode
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String visitCode) {
        if (subjectId != null) scopeGuard.assertSubjectAccess(subjectId);
        else if (!scopeGuard.currentScope().admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可查询全部访视");
        }

        StringBuilder sql = new StringBuilder("SELECT s.*, sub.subject_code as subjectCode FROM session s LEFT JOIN subject sub ON s.subject_id = sub.id WHERE s.is_active = 1");
        List<Object> params = new ArrayList<>();

        if (subjectId != null) { sql.append(" AND s.subject_id = ?"); params.add(subjectId); }
        if (visitCode != null && !visitCode.isEmpty()) { sql.append(" AND s.visit_label = ?"); params.add(visitCode); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql.toString().replace("*", "1") + ") cnt", Long.class, params.toArray());
        sql.append(" ORDER BY s.session_date DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), params.toArray());
        return ApiResponse.ok(Map.of("records", records, "total", total != null ? total : 0, "page", page, "size", size));
    }

    /**
     * GET /api/v1/sessions/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        scopeGuard.assertSessionAccess(id);
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT s.*, sub.subject_code as subjectCode FROM session s LEFT JOIN subject sub ON s.subject_id = sub.id WHERE s.id = ?", id);
        return ApiResponse.ok(row);
    }

    /**
     * PUT /api/v1/sessions/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scopeGuard.assertSessionAccess(id);
        String sql = "UPDATE session SET visit_label=COALESCE(?,visit_label), session_date=COALESCE(?,session_date), status=COALESCE(?,status), notes=COALESCE(?,notes) WHERE id=?";
        jdbc.update(sql,
            body.get("visitLabel"), body.get("sessionDate"), body.get("status"), body.get("notes"), id);
        return getById(id);
    }

    /**
     * PATCH /api/v1/sessions/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, String>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scopeGuard.assertSessionAccess(id);
        String status = (String) body.getOrDefault("status", "SCHEDULED");
        jdbc.update("UPDATE session SET status=? WHERE id=?", status, id);
        return ApiResponse.ok(Map.of("message", "Status updated", "status", status));
    }

    /**
     * GET /api/v1/sessions/{sessionId}/assessments
     */
    @GetMapping("/{sessionId}/assessments")
    public ApiResponse<List<Map<String, Object>>> getAssessments(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT sa.*, si.name as instrumentName FROM scale_assessment sa LEFT JOIN scale_instrument si ON sa.instrument_id = si.id WHERE sa.session_id = ? ORDER BY sa.assessment_date DESC", sessionId);
        return ApiResponse.ok(rows);
    }

    /**
     * GET /api/v1/sessions/{sessionId}/imaging
     */
    @GetMapping("/{sessionId}/imaging")
    public ApiResponse<List<Map<String, Object>>> getImaging(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM imaging_session WHERE session_id = ? ORDER BY acquisition_date DESC", sessionId);
        return ApiResponse.ok(rows);
    }

    /**
     * GET /api/v1/sessions/{sessionId}/lab-tests
     */
    @GetMapping("/{sessionId}/lab-tests")
    public ApiResponse<List<Map<String, Object>>> getLabTests(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM lab_result WHERE session_id = ? ORDER BY test_date DESC", sessionId);
        return ApiResponse.ok(rows);
    }

    /**
     * POST /api/v1/sessions/{sessionId}/copy-from-last
     * Copies the most recent session for the same subject, increments visit number.
     */
    @PostMapping("/{sessionId}/copy-from-last")
    public ApiResponse<Map<String, Object>> copyFromLast(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        // Find the source session
        Map<String, Object> src = jdbc.queryForMap("SELECT * FROM session WHERE id = ?", sessionId);
        Long subjectId = ((Number) src.get("subject_id")).longValue();
        Integer lastNum = src.get("visit_number") != null ? ((Number) src.get("visit_number")).intValue() : 0;

        // Insert new session
        jdbc.update(
            "INSERT INTO session (session_code, subject_id, project_id, institution_id, visit_label, visit_number, session_date, status, is_active, created_at) " +
            "SELECT CONCAT('CP-', ?), subject_id, project_id, institution_id, ?, ?, ?, 'SCHEDULED', 1, NOW() FROM session WHERE id = ?",
            lastNum + 1, "Visit " + (lastNum + 1), lastNum + 1, LocalDate.now().plusDays(30), sessionId);

        // Get the new ID
        Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getById(newId);
    }
}
