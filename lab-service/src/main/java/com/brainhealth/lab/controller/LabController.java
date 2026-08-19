package com.brainhealth.lab.controller;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.lab.entity.*;
import com.brainhealth.lab.service.LabService;
import com.brainhealth.lab.service.LabReportUploadService;
import com.brainhealth.lab.service.LabReportParsingService;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/lab")
public class LabController {
    private final LabService service;
    private final LabReportUploadService reportUploadService;
    private final LabReportParsingService reportParsingService;
    private final DataScopeGuard scopeGuard;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public LabController(LabService service, LabReportUploadService reportUploadService,
                         LabReportParsingService reportParsingService, DataScopeGuard scopeGuard,
                         JdbcTemplate jdbc) {
        this.service = service;
        this.reportUploadService = reportUploadService;
        this.reportParsingService = reportParsingService;
        this.scopeGuard = scopeGuard;
        this.jdbc = jdbc;
    }

    @GetMapping("/results")
    public ApiResponse<PageResult<LabResult>> listResults(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long labTestId,
            @RequestParam(required = false) Boolean isAbnormal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        else if (subjectId != null) scopeGuard.assertSubjectAccess(subjectId);
        else if (!scopeGuard.currentScope().admin()) {
            return ApiResponse.ok(service.listResultsForSubjects(scopeGuard.accessibleSubjectIds(), page, size));
        }
        return ApiResponse.ok(service.listResults(sessionId, subjectId, labTestId, isAbnormal, page, size));
    }

    @GetMapping("/results/{id}")
    public ApiResponse<LabResult> getResult(@PathVariable Long id) {
        LabResult result = service.getResult(id);
        if (result == null) throw new IllegalArgumentException("检验结果不存在");
        scopeGuard.assertSubjectAccess(result.getSubjectId());
        return ApiResponse.ok(result);
    }

    @PostMapping("/results")
    public ApiResponse<LabResult> createResult(@RequestBody LabResult data) {
        scopeGuard.assertSubjectAccess(data.getSubjectId());
        scopeGuard.assertSessionAccess(data.getSessionId());
        return ApiResponse.created(service.createResult(data));
    }

    @PutMapping("/results/{id}")
    public ApiResponse<LabResult> updateResult(@PathVariable Long id, @RequestBody LabResult data) {
        LabResult current = service.getResult(id);
        if (current == null) throw new IllegalArgumentException("检验结果不存在");
        scopeGuard.assertSubjectAccess(current.getSubjectId());
        return ApiResponse.ok(service.updateResult(id, data));
    }

    @DeleteMapping("/results/{id}")
    public ApiResponse<Void> deleteResult(@PathVariable Long id) {
        LabResult current = service.getResult(id);
        if (current == null) throw new IllegalArgumentException("检验结果不存在");
        scopeGuard.assertSubjectAccess(current.getSubjectId());
        service.deleteResult(id); return ApiResponse.ok(null);
    }

    @PostMapping("/results/batch")
    public ApiResponse<List<LabResult>> batchCreate(@RequestBody BatchLabResults body) {
        scopeGuard.assertSessionAccess(body.sessionId());
        if (body.sessionId() == null || body.sessionId() < 1) {
            throw new IllegalArgumentException("访视 ID 不能为空");
        }
        if (body.results() == null || body.results().isEmpty()) {
            throw new IllegalArgumentException("没有可导入的检验结果");
        }
        body.results().forEach(result -> result.setSessionId(body.sessionId()));
        return ApiResponse.created(service.batchCreate(body.sessionId(), body.results()));
    }

    @GetMapping("/tests")
    public ApiResponse<List<LabTestItem>> listTests(@RequestParam(required = false) String category, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listTests(category, keyword));
    }

    @GetMapping("/tests/{id}")
    public ApiResponse<LabTestItem> getTest(@PathVariable Long id) { return ApiResponse.ok(service.getTest(id)); }

    @GetMapping("/dynamic-fields")
    public ApiResponse<List<Map<String, Object>>> dynamicFields() {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT fd.id,fd.field_code AS fieldCode,fd.label,fd.description,
                   fd.field_type AS fieldType,fd.unit,fd.default_value AS defaultValue,
                   fd.options_json AS options,fd.validation_json AS validation,
                   fd.required_flag AS requiredFlag,fd.sort_order AS sortOrder,f.version
            FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id
            WHERE f.module='LAB' AND f.status='PUBLISHED' AND fd.status='PUBLISHED'
            ORDER BY fd.sort_order,fd.id
            """));
    }

    @GetMapping("/sessions/{sessionId}/dynamic-values")
    public ApiResponse<Map<String, Object>> dynamicValues(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
            SELECT fd.field_code,fv.value_json
            FROM field_value fv JOIN field_definition fd ON fd.id=fv.field_id
            WHERE fv.entity_type='LAB_SESSION' AND fv.entity_id=?
            """, sessionId)) {
            Object raw = row.get("value_json");
            try {
                result.put(String.valueOf(row.get("field_code")),
                    raw == null ? null : objectMapper.readValue(raw.toString(), Object.class));
            } catch (Exception ignored) { result.put(String.valueOf(row.get("field_code")), raw); }
        }
        return ApiResponse.ok(result);
    }

    @PutMapping("/sessions/{sessionId}/dynamic-values")
    public ApiResponse<Map<String, String>> saveDynamicValues(
            @PathVariable Long sessionId, @RequestBody Map<String, Object> values,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        scopeGuard.assertSessionAccess(sessionId);
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
            SELECT fd.id,fd.field_code,f.version
            FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id
            WHERE f.module='LAB' AND f.status='PUBLISHED' AND fd.status='PUBLISHED'
            """)) fields.put(String.valueOf(row.get("field_code")), row);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Map<String, Object> field = fields.get(entry.getKey());
            if (field == null) continue;
            try {
                jdbc.update("""
                    INSERT INTO field_value(field_id,entity_type,entity_id,value_json,form_version,created_by)
                    VALUES (?,'LAB_SESSION',?,?,?,?)
                    ON DUPLICATE KEY UPDATE value_json=VALUES(value_json),updated_at=NOW()
                    """, field.get("id"), sessionId, objectMapper.writeValueAsString(entry.getValue()),
                    field.get("version"), userId);
            } catch (Exception ex) { throw new IllegalArgumentException("扩展字段值格式错误", ex); }
        }
        return ApiResponse.ok(Map.of("message", "实验室扩展字段已保存"));
    }

    @PostMapping("/report-uploads")
    public ApiResponse<LabReportUpload> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long subjectId,
            @RequestParam Long sessionId) {
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.created(reportUploadService.store(file, subjectId, sessionId));
    }

    @GetMapping("/report-uploads")
    public ApiResponse<List<LabReportUpload>> listReportUploads(
            @RequestParam Long subjectId, @RequestParam Long sessionId) {
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(reportUploadService.list(subjectId, sessionId));
    }

    @GetMapping("/report-uploads/{id}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable String id) {
        LabReportUpload upload = reportUploadService.get(id);
        scopeGuard.assertSubjectAccess(upload.getSubjectId());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(upload.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(upload.getOriginalName(), StandardCharsets.UTF_8).build().toString())
            .body(reportUploadService.resource(id));
    }

    @DeleteMapping("/report-uploads/{id}")
    public ApiResponse<Void> deleteReport(@PathVariable String id) {
        scopeGuard.assertSubjectAccess(reportUploadService.get(id).getSubjectId());
        reportUploadService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/report-uploads/{id}/preview")
    public ApiResponse<LabReportParsingService.Preview> previewReport(@PathVariable String id) {
        scopeGuard.assertSubjectAccess(reportUploadService.get(id).getSubjectId());
        return ApiResponse.ok(reportParsingService.preview(id));
    }

    @PostMapping("/report-uploads/{id}/confirm")
    public ApiResponse<List<LabResult>> confirmReport(
            @PathVariable String id,
            @RequestBody List<LabReportParsingService.Candidate> candidates) {
        scopeGuard.assertSubjectAccess(reportUploadService.get(id).getSubjectId());
        return ApiResponse.created(reportParsingService.confirm(id, candidates));
    }

    public record BatchLabResults(Long sessionId, List<LabResult> results) { }
}
