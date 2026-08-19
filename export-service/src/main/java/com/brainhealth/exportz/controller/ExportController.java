package com.brainhealth.exportz.controller;

import com.brainhealth.common.model.*;
import com.brainhealth.common.security.DataScopeGuard;
import com.brainhealth.exportz.entity.ExportRequest;
import com.brainhealth.exportz.repository.ExportRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {
    private static final Set<String> TYPES = Set.of("subjects", "assessments", "lab_results");
    private final ExportRequestRepository repo;
    private final JdbcTemplate jdbc;
    private final DataScopeGuard scopeGuard;
    private final HttpServletRequest httpRequest;

    public ExportController(ExportRequestRepository repo, JdbcTemplate jdbc,
                            DataScopeGuard scopeGuard, HttpServletRequest httpRequest) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
        this.httpRequest = httpRequest;
    }

    @GetMapping("/requests")
    public ApiResponse<PageResult<ExportRequest>> listRequests(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String exportType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, size)),
            Sort.by("createdAt").descending());
        Page<ExportRequest> result;
        if (scopeGuard.currentScope().admin()) {
            if (projectId != null) result = repo.findByProjectId(projectId, pageable);
            else if (status != null) result = repo.findByStatus(status, pageable);
            else if (exportType != null) result = repo.findByExportType(exportType, pageable);
            else result = repo.findAll(pageable);
        } else {
            result = repo.findByRequesterId(currentUserId(), pageable);
        }
        List<ExportRequest> records = result.getContent().stream()
            .filter(item -> requestId == null || item.getRequestId().contains(requestId))
            .filter(item -> status == null || status.equals(item.getStatus()))
            .filter(item -> projectId == null || projectId.equals(item.getProjectId()))
            .filter(item -> exportType == null || exportType.equals(item.getExportType())).toList();
        return ApiResponse.ok(PageResult.of(page, size, result.getTotalElements(), records));
    }

    @PostMapping("/requests")
    public ApiResponse<ExportRequest> createRequest(@RequestBody Map<String, Object> body) {
        String exportType = String.valueOf(body.getOrDefault("exportType", "subjects"));
        if (!TYPES.contains(exportType)) throw new IllegalArgumentException("不支持的导出类型");
        Long projectId = requiredLong(body, "projectId");
        scopeGuard.assertProjectAccess(projectId);
        ExportRequest request = new ExportRequest();
        request.setRequestId("EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setExportType(exportType);
        request.setFormatType("CSV");
        request.setProjectId(projectId);
        request.setProjectName(jdbc.queryForObject("SELECT name FROM project WHERE id=?", String.class, projectId));
        request.setRequesterId(currentUserId());
        request.setRequesterName(Optional.ofNullable(httpRequest.getHeader("X-Username")).orElse(""));
        request.setReason(String.valueOf(body.getOrDefault("reason", "")));
        request.setDataScopeSummary("项目ID=" + projectId + "；类型=" + exportType);
        request.setStatus("PENDING");
        return ApiResponse.created(repo.save(request));
    }

    @GetMapping("/requests/{id}")
    public ApiResponse<ExportRequest> getRequest(@PathVariable Long id) {
        return ApiResponse.ok(requireAuthorized(id));
    }

    @DeleteMapping("/requests/{id}")
    public ApiResponse<Void> deleteRequest(@PathVariable Long id) {
        ExportRequest request = requireAuthorized(id);
        if (!Set.of("PENDING", "REJECTED").contains(request.getStatus())) {
            throw new IllegalArgumentException("仅待审核或已拒绝的申请可以删除");
        }
        repo.delete(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/requests/{id}/review")
    public ApiResponse<ExportRequest> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!scopeGuard.currentScope().admin()) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.FORBIDDEN, "仅管理员可审核导出申请");
        ExportRequest request = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("导出申请不存在"));
        String action = body.get("action");
        if (!Set.of("APPROVED", "REJECTED").contains(action)) throw new IllegalArgumentException("审核动作无效");
        request.setStatus(action);
        request.setReviewComment(body.getOrDefault("comment", ""));
        request.setReviewerId(currentUserId());
        request.setReviewerName(Optional.ofNullable(httpRequest.getHeader("X-Username")).orElse(""));
        request.setReviewedAt(LocalDateTime.now());
        return ApiResponse.ok(repo.save(request));
    }

    @GetMapping("/requests/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ExportRequest request = requireAuthorized(id);
        if (!"APPROVED".equals(request.getStatus()) && !"COMPLETED".equals(request.getStatus())) {
            throw new IllegalArgumentException("导出申请尚未审核通过");
        }
        scopeGuard.assertProjectAccess(request.getProjectId());
        List<Map<String, Object>> rows = exportRows(request);
        byte[] content = toCsv(rows).getBytes(StandardCharsets.UTF_8);
        request.setStatus("COMPLETED");
        request.setFileSize((long) content.length);
        request.setTotalRecords(rows.size());
        repo.save(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export_" + id + ".csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(content);
    }

    private List<Map<String, Object>> exportRows(ExportRequest request) {
        Long projectId = request.getProjectId();
        return switch (request.getExportType()) {
            case "assessments" -> jdbc.queryForList(
                "SELECT DISTINCT s.subject_code subjectId, si.name instrument, a.total_score score, "
                    + "a.assessment_date date, a.data_entry_status status FROM scale_assessment a "
                    + "JOIN subject s ON s.id=a.subject_id JOIN scale_instrument si ON si.id=a.instrument_id "
                    + "JOIN subject_cohort sc ON sc.subject_id=s.id JOIN cohort c ON c.id=sc.cohort_id "
                    + "WHERE c.project_id=? ORDER BY s.subject_code,a.assessment_date", projectId);
            case "lab_results" -> jdbc.queryForList(
                "SELECT DISTINCT s.subject_code subjectId, t.name testName, r.result, r.unit, "
                    + "r.collection_date date, r.is_abnormal isAbnormal FROM lab_result r "
                    + "JOIN subject s ON s.id=r.subject_id JOIN lab_test_item t ON t.id=r.lab_test_id "
                    + "JOIN subject_cohort sc ON sc.subject_id=s.id JOIN cohort c ON c.id=sc.cohort_id "
                    + "WHERE c.project_id=? ORDER BY s.subject_code,r.collection_date", projectId);
            default -> jdbc.queryForList(
                "SELECT DISTINCT s.subject_code subjectId, s.last_name lastName, s.first_name firstName, "
                    + "sx.name sex, s.birth_date birthDate, s.education_years educationYears, "
                    + "i.name institution, p.name project FROM subject s "
                    + "LEFT JOIN sex_code sx ON sx.id=s.sex_code_id "
                    + "LEFT JOIN institution i ON i.id=s.enrollment_institution_id "
                    + "JOIN subject_cohort sc ON sc.subject_id=s.id JOIN cohort c ON c.id=sc.cohort_id "
                    + "JOIN project p ON p.id=c.project_id WHERE p.id=? ORDER BY s.subject_code", projectId);
        };
    }

    private ExportRequest requireAuthorized(Long id) {
        ExportRequest request = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("导出申请不存在"));
        if (!scopeGuard.currentScope().admin() && !Objects.equals(request.getRequesterId(), currentUserId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该导出申请");
        }
        return request;
    }

    private long currentUserId() {
        try { return Long.parseLong(httpRequest.getHeader("X-User-Id")); }
        catch (Exception e) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }

    private static Long requiredLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) throw new IllegalArgumentException("缺少参数 " + key);
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private static String toCsv(List<Map<String, Object>> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        if (rows.isEmpty()) return output.toString();
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        output.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            for (int index = 0; index < headers.size(); index++) {
                if (index > 0) output.append(',');
                output.append(escape(row.get(headers.get(index))));
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static String escape(Object value) {
        if (value == null) return "";
        String text = value.toString().replace("\"", "\"\"");
        return text.matches(".*[,\"\\r\\n].*") ? "\"" + text + "\"" : text;
    }
}
