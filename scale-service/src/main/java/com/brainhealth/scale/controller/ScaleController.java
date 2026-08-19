package com.brainhealth.scale.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.brainhealth.scale.dto.*;
import com.brainhealth.scale.service.ClinicalFieldDictionaryService;
import com.brainhealth.scale.service.ScaleService;
import com.brainhealth.scale.entity.ScaleData;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.brainhealth.common.constant.Constants;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1")
public class ScaleController {
    private static final Logger log = LoggerFactory.getLogger(ScaleController.class);

    private final ScaleService scaleService;
    private final ClinicalFieldDictionaryService fieldDictionaryService;
    private final DataScopeGuard scopeGuard;

    public ScaleController(ScaleService scaleService, ClinicalFieldDictionaryService fieldDictionaryService,
                           DataScopeGuard scopeGuard) {
        this.scaleService = scaleService;
        this.fieldDictionaryService = fieldDictionaryService;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping("/clinical-fields/summary")
    public ApiResponse<Map<String, Object>> clinicalFieldSummary() {
        return ApiResponse.ok(fieldDictionaryService.summary());
    }

    @GetMapping("/clinical-fields")
    public ApiResponse<PageResult<JsonNode>> clinicalFields(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String formCode,
            @RequestParam(required = false) String visitCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(fieldDictionaryService.search(category, formCode, visitCode, keyword, page, size));
    }

    @GetMapping("/scales")
    public ApiResponse<List<InstrumentDTO>> listInstruments(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scaleService.listInstruments(category, keyword));
    }

    @GetMapping("/scales/patient/tasks")
    public ApiResponse<List<Map<String, Object>>> patientTasks() {
        scopeGuard.assertAnyRole("PATIENT");
        Long subjectId = scopeGuard.currentSubjectId();
        if (subjectId == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.FORBIDDEN, "患者账号未绑定受试者");
        return ApiResponse.ok(scaleService.patientTasks(subjectId));
    }

    @GetMapping("/scales/subjects/{subjectId}/tasks")
    public ApiResponse<List<Map<String, Object>>> subjectTasks(@PathVariable Long subjectId) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI");
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(scaleService.patientTasks(subjectId));
    }

    @GetMapping("/scales/admin/item-overrides")
    public ApiResponse<List<Map<String, Object>>> itemOverrides(@RequestParam String visitCode) {
        scopeGuard.assertAnyRole("ADMIN");
        return ApiResponse.ok(scaleService.listItemOverrides(visitCode));
    }

    @PutMapping("/scales/admin/item-overrides")
    public ApiResponse<Map<String, String>> updateItemOverride(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        scopeGuard.assertAnyRole("ADMIN");
        scaleService.updateItemOverride(
            String.valueOf(body.get("visitCode")), String.valueOf(body.get("scaleCode")),
            String.valueOf(body.get("itemCode")),
            body.get("label") == null ? null : String.valueOf(body.get("label")),
            body.get("required") instanceof Boolean value ? value : null,
            body.get("status") == null ? "PUBLISHED" : String.valueOf(body.get("status")), userId);
        return ApiResponse.ok(Map.of("message", "Item override updated"));
    }

    @GetMapping("/scales/{id}")
    public ApiResponse<InstrumentDetailDTO> getInstrument(@PathVariable Long id) {
        return ApiResponse.ok(scaleService.getInstrument(id));
    }

    @PostMapping("/assessments")
    public ApiResponse<AssessmentDTO> createAssessment(@RequestBody AssessmentCreateRequest req) {
        scopeGuard.assertSubjectAccess(req.getSubjectId());
        scopeGuard.assertSessionAccess(req.getSessionId());
        return ApiResponse.created(scaleService.createAssessment(req));
    }

    @GetMapping("/assessments/{id}")
    public ApiResponse<AssessmentDTO> getAssessment(@PathVariable Long id) {
        AssessmentDTO assessment = scaleService.getAssessment(id);
        scopeGuard.assertSubjectAccess(assessment.getSubjectId());
        return ApiResponse.ok(assessment);
    }

    @GetMapping("/assessments/{id}/items")
    public ApiResponse<List<ScaleData>> getAssessmentItems(@PathVariable Long id) {
        scopeGuard.assertSubjectAccess(scaleService.getAssessment(id).getSubjectId());
        return ApiResponse.ok(scaleService.getAssessmentItems(id));
    }

    @PostMapping("/assessments/{id}/items")
    public ApiResponse<List<ScaleData>> saveAssessmentItems(
            @PathVariable Long id, @RequestBody AssessmentItemRequest req) {
        scopeGuard.assertSubjectAccess(scaleService.getAssessment(id).getSubjectId());
        return ApiResponse.ok(scaleService.saveAssessmentItems(id, req));
    }

    @PostMapping("/assessments/{id}/submit")
    public ApiResponse<AssessmentDTO> submitAssessment(@PathVariable Long id,
                                                        @RequestBody AssessmentSubmitRequest req) {
        scopeGuard.assertSubjectAccess(scaleService.getAssessment(id).getSubjectId());
        return ApiResponse.ok(scaleService.submitAssessment(id, req));
    }

    @GetMapping("/subjects/{subjectId}/assessments")
    public ApiResponse<List<AssessmentDTO>> getSubjectAssessments(@PathVariable Long subjectId) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(scaleService.getSubjectAssessments(subjectId));
    }

    @GetMapping("/sessions/{sessionId}/assessments")
    public ApiResponse<List<AssessmentDTO>> getSessionAssessments(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(scaleService.getSessionAssessments(sessionId));
    }

    // ---------------------------------------------------------------
    // Visit-based scale form endpoints
    // ---------------------------------------------------------------

    /**
     * GET /api/v1/scales/visit-form/{visitCode}
     * Returns the complete CRF form for a visit, grouped by scale instrument.
     * Reads from scale_metadata.json (generated by the Python engine).
     */
    @GetMapping("/scales/visit-form/{visitCode}")
    public ApiResponse<VisitFormResponse> getVisitForm(@PathVariable String visitCode) {
        return ApiResponse.ok(scaleService.getVisitForm(visitCode));
    }

    /**
     * POST /api/v1/scales/compute
     * Computes scale scores from user responses.
     * Falls back to summing numeric response values when Python engine is unavailable.
     */
    @PostMapping("/scales/compute")
    public ApiResponse<ComputeResponse> computeScale(@RequestBody ComputeRequest req) {
        return ApiResponse.ok(scaleService.computeScore(req));
    }

    /**
     * GET /api/v1/scales/visit-form/{visitCode}/scale/{scaleCode}
     * Lazy-loads items for a single scale in a visit to avoid loading all 6000+ items at once.
     */
    @GetMapping("/scales/visit-form/{visitCode}/scale/{scaleCode}")
    public ApiResponse<ScaleFormDTO> getVisitScaleItems(
            @PathVariable String visitCode,
            @PathVariable String scaleCode) {
        return ApiResponse.ok(scaleService.getVisitScaleItems(visitCode, scaleCode));
    }

    /**
     * GET /api/v1/assessments — Query assessments by subjectId or sessionId
     */
    @GetMapping("/assessments")
    public ApiResponse<List<Map<String, Object>>> listAssessments(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sessionId) {
        if (subjectId != null) scopeGuard.assertSubjectAccess(subjectId);
        else if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        else if (!scopeGuard.currentScope().admin()) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.FORBIDDEN, "仅管理员可查询全部评估");
        return ApiResponse.ok(scaleService.listAssessments(subjectId, sessionId));
    }

    /**
     * POST /api/v1/scales/save-draft — Save draft responses
     */
    @PostMapping("/scales/save-draft")
    public ApiResponse<String> saveDraft(@RequestBody java.util.Map<String, Object> body) {
        Long subjectId = requiredLong(body, "subjectId");
        scopeGuard.assertSubjectAccess(subjectId);
        if (scopeGuard.currentScope().roles().stream().anyMatch(role -> role.endsWith("PATIENT"))) {
            scaleService.assertPatientTaskEditable(subjectId, requiredLong(body, "sessionId"));
        }
        scaleService.saveDraft(body);
        return ApiResponse.ok("draft-saved");
    }

    @GetMapping("/scales/responses")
    public ApiResponse<java.util.Map<String, Object>> getSavedResponses(
            @RequestParam Long subjectId, @RequestParam String visitCode,
            @RequestParam(required = false) Long sessionId) {
        scopeGuard.assertSubjectAccess(subjectId);
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(scaleService.loadResponses(subjectId, visitCode, sessionId));
    }

    /**
     * POST /api/v1/scales/submit — Submit all scale responses for a visit
     */
    @PostMapping("/scales/submit")
    public ApiResponse<String> submitAll(
            @RequestBody java.util.Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        Long subjectId = requiredLong(body, "subjectId");
        var scope = scopeGuard.currentScope();
        log.info("Visit form submit: userId={}, roles={}, subjectId={}, boundSubjectId={}",
            userId, scope.roles(), subjectId, scope.subjectId());
        scopeGuard.assertSubjectAccess(subjectId);
        if (scope.roles().stream().anyMatch(role -> role.endsWith("PATIENT"))) {
            scaleService.assertPatientTaskEditable(subjectId, requiredLong(body, "sessionId"));
        }
        scaleService.submitResponses(body);
        scaleService.recordAudit(userId, "PATIENT_SCALE_SUBMIT", "SESSION",
            requiredLong(body, "sessionId"), "患者提交访视量表");
        return ApiResponse.ok("submitted");
    }

    @PostMapping("/scales/tasks/{taskId}/return")
    public ApiResponse<Map<String, String>> returnPatientTask(
            @PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI");
        scopeGuard.assertSubjectAccess(scaleService.patientTaskSubject(taskId));
        String reason = body == null || body.get("reason") == null
            ? "请补充或修正后重新提交" : String.valueOf(body.get("reason"));
        scaleService.returnPatientTask(taskId, reason);
        scaleService.recordAudit(userId, "ASSESSMENT_TASK_RETURN", "ASSESSMENT_TASK",
            taskId, reason);
        return ApiResponse.ok(Map.of("message", "任务已退回患者"));
    }

    /**
     * GET /api/v1/scales/visit-progress/{subjectId}
     * Returns completion status per visit per scale for a subject.
     */
    @GetMapping("/scales/visit-progress/{subjectId}")
    public ApiResponse<VisitProgressResponse> getVisitProgress(@PathVariable Long subjectId) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(scaleService.getVisitProgress(subjectId));
    }

    /**
     * GET /api/v1/scales/scores/{subjectId}
     * Returns all scale scores for a subject across all visits,
     * with history, subscales, interpretation, and flag for the Score Center UI.
     */
    @GetMapping("/scales/scores/{subjectId}")
    public ApiResponse<List<Map<String, Object>>> getSubjectScores(@PathVariable Long subjectId) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(scaleService.getSubjectScores(subjectId));
    }

    private static Long requiredLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("缺少参数 " + key);
        return Long.valueOf(value.toString());
    }
}
