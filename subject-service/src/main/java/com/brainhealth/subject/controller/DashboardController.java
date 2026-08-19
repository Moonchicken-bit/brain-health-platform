package com.brainhealth.subject.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final DataScopeGuard scopeGuard;

    public DashboardController(JdbcTemplate jdbc, DataScopeGuard scopeGuard) {
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        DataScopeGuard.Scope scope = scopeGuard.currentScope();
        ScopeSql subjectScope = subjectScope(scope, "s");
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalSubjects", count(
                "SELECT COUNT(*) FROM subject s WHERE s.is_active=1" + subjectScope.sql(),
                subjectScope.params()));

        List<Object> monthParams = new ArrayList<>();
        monthParams.add(LocalDate.now().withDayOfMonth(1).toString());
        monthParams.addAll(subjectScope.params());
        result.put("newThisMonth", count(
                "SELECT COUNT(*) FROM subject s WHERE s.is_active=1 AND s.created_at>=?" + subjectScope.sql(),
                monthParams));

        ScopeSql assessmentScope = subjectScope(scope, "sub");
        result.put("pendingAssessments", count(
                "SELECT COUNT(*) FROM scale_assessment sa JOIN subject sub ON sub.id=sa.subject_id " +
                "WHERE sa.data_entry_status='Incomplete'" + assessmentScope.sql(),
                assessmentScope.params()));

        ScopeSql imagingScope = subjectScope(scope, "sub");
        result.put("pendingImagingQC", count(
                "SELECT COUNT(*) FROM imaging_series ser " +
                "JOIN imaging_session ims ON ims.id=ser.imaging_session_id " +
                "JOIN subject sub ON sub.id=ims.subject_id WHERE UPPER(ser.qc_status)='PENDING'" +
                imagingScope.sql(), imagingScope.params()));

        result.put("genderBreakdown", jdbc.queryForList(
                "SELECT COALESCE(s.sex,'未知') AS label, COUNT(*) AS count FROM subject s " +
                "WHERE s.is_active=1" + subjectScope.sql() + " GROUP BY s.sex",
                subjectScope.params().toArray()));

        result.put("projectBreakdown", jdbc.queryForList(
                "SELECT p.name AS projectName, COUNT(DISTINCT s.id) AS count " +
                "FROM subject s JOIN project p ON p.id=s.project_id " +
                "WHERE s.is_active=1" + subjectScope.sql() + " GROUP BY p.id,p.name",
                subjectScope.params().toArray()));

        result.put("assessmentBreakdown", jdbc.queryForList(
                "SELECT sa.data_entry_status AS status, COUNT(*) AS count " +
                "FROM scale_assessment sa JOIN subject sub ON sub.id=sa.subject_id WHERE 1=1" +
                assessmentScope.sql() + " GROUP BY sa.data_entry_status",
                assessmentScope.params().toArray()));

        result.put("pendingAssessmentsList", jdbc.queryForList(
                "SELECT t.subject_id AS subjectId,sub.subject_code AS subjectLabel," +
                "t.visit_code AS visitCode,COALESCE(sess.visit_label,t.visit_code) AS visitName," +
                "'访视量表任务' AS instrumentName,t.due_at AS dueDate " +
                "FROM assessment_task t JOIN subject sub ON sub.id=t.subject_id " +
                "LEFT JOIN session sess ON sess.id=t.session_id " +
                "WHERE t.status IN ('PENDING','IN_PROGRESS','RETURNED')" +
                assessmentScope.sql() + " ORDER BY COALESCE(t.due_at,'9999-12-31'),t.id LIMIT 10",
                assessmentScope.params().toArray()));

        result.put("scoreAlerts", jdbc.queryForList(
                "SELECT sa.subject_id AS subjectId,sub.subject_code AS subjectLabel," +
                "i.name AS instrumentName,sa.total_score AS currentScore," +
                "COALESCE((SELECT prior.total_score FROM scale_assessment prior " +
                "WHERE prior.subject_id=sa.subject_id AND prior.instrument_id=sa.instrument_id " +
                "AND prior.id<sa.id AND prior.total_score IS NOT NULL ORDER BY prior.id DESC LIMIT 1)," +
                "sa.total_score) AS previousScore,sa.interpretation,COALESCE(sess.visit_label,'') AS visitCode " +
                "FROM scale_assessment sa JOIN subject sub ON sub.id=sa.subject_id " +
                "JOIN scale_instrument i ON i.id=sa.instrument_id " +
                "LEFT JOIN session sess ON sess.id=sa.session_id " +
                "WHERE sa.total_score IS NOT NULL AND UPPER(COALESCE(sa.severity,'')) " +
                "IN ('MILD','MODERATE','SEVERE')" + assessmentScope.sql() +
                " ORDER BY sa.assessment_date DESC,sa.id DESC LIMIT 10",
                assessmentScope.params().toArray()));

        result.put("recentSubjects", jdbc.queryForList(
                "SELECT s.subject_code AS subjectId, s.sex, s.birth_date AS dateOfBirth, " +
                "s.created_at AS registeredAt, GROUP_CONCAT(DISTINCT p.name ORDER BY p.name SEPARATOR '、') AS projectName " +
                "FROM subject s LEFT JOIN project p ON p.id=s.project_id " +
                "WHERE s.is_active=1" + subjectScope.sql() +
                " GROUP BY s.id,s.subject_code,s.sex,s.birth_date,s.created_at " +
                "ORDER BY s.created_at DESC LIMIT 5",
                subjectScope.params().toArray()));
        return ApiResponse.ok(result);
    }

    private Long count(String sql, List<Object> params) {
        Long value = jdbc.queryForObject(sql, Long.class, params.toArray());
        return value == null ? 0L : value;
    }

    private ScopeSql subjectScope(DataScopeGuard.Scope scope, String alias) {
        if (scope.admin()) return new ScopeSql("", List.of());
        List<String> branches = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (scope.institutionId() != null) {
            branches.add("COALESCE(" + alias + ".enrollment_institution_id," + alias + ".institution_id)=?");
            params.add(scope.institutionId());
        }
        if (!scope.projectIds().isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(scope.projectIds().size(), "?"));
            branches.add(alias + ".project_id IN (" + placeholders + ")");
            params.addAll(scope.projectIds());
        }
        return branches.isEmpty()
                ? new ScopeSql(" AND 1=0", List.of())
                : new ScopeSql(" AND (" + String.join(" OR ", branches) + ")", params);
    }

    private record ScopeSql(String sql, List<Object> params) {}
}
