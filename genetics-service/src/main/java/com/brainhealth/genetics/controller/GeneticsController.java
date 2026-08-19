package com.brainhealth.genetics.controller;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.genetics.entity.*;
import com.brainhealth.genetics.service.GeneticsService;
import com.brainhealth.genetics.service.GeneticsUploadService;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/genetics")
public class GeneticsController {
    private final GeneticsService service;
    private final GeneticsUploadService uploadService;
    private final DataScopeGuard scopeGuard;
    private final JdbcTemplate jdbc;
    public GeneticsController(GeneticsService service, GeneticsUploadService uploadService,
                              DataScopeGuard scopeGuard, JdbcTemplate jdbc) {
        this.service = service;
        this.uploadService = uploadService;
        this.scopeGuard = scopeGuard;
        this.jdbc = jdbc;
    }

    @GetMapping("/samples")
    public ApiResponse<PageResult<GeneticsSample>> listSamples(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String geneSymbol,
            @RequestParam(required = false) String variantType,
            @RequestParam(required = false) String clinicalSignificance,
            @RequestParam(required = false) String sampleType,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String qcStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (subjectId != null) scopeGuard.assertSubjectAccess(subjectId);
        else if (!scopeGuard.currentScope().admin()) {
            return ApiResponse.ok(service.listSamplesForSubjects(scopeGuard.accessibleSubjectIds(), page, size));
        }
        return ApiResponse.ok(service.listSamples(subjectId, sampleType, platform, qcStatus, page, size));
    }

    @GetMapping("/samples/{id}")
    public ApiResponse<GeneticsSample> getSample(@PathVariable Long id) {
        GeneticsSample sample = requireSample(id);
        scopeGuard.assertSubjectAccess(sample.getSubjectId());
        return ApiResponse.ok(sample);
    }

    @DeleteMapping("/samples/{id}")
    public ApiResponse<Void> deleteSample(@PathVariable Long id) {
        scopeGuard.assertSubjectAccess(requireSample(id).getSubjectId());
        service.deleteSample(id); return ApiResponse.ok(null);
    }

    @PostMapping("/samples/upload")
    public ApiResponse<GeneticsSample> uploadVcf(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long subjectId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String referenceGenome,
            @RequestParam(required = false) String sampleType) {
        scopeGuard.assertSubjectAccess(subjectId);
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.created(uploadService.storeDirect(
                file, subjectId, sessionId, platform, referenceGenome, sampleType));
    }

    @PostMapping("/upload/chunk")
    public ApiResponse<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam int totalChunks,
            @RequestParam long fileSize,
            @RequestParam String fileName) {
        uploadService.storeChunk(file, uploadId, chunkIndex, totalChunks, fileSize, fileName);
        return ApiResponse.ok(Map.of(
                "uploadId", uploadId,
                "chunkIndex", chunkIndex,
                "received", true));
    }

    @PostMapping("/upload/merge")
    public ApiResponse<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body) {
        Long subjectId = requiredLong(body, "subjectId");
        Long sessionId = optionalLong(body, "sessionId");
        scopeGuard.assertSubjectAccess(subjectId);
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        GeneticsSample saved = uploadService.merge(
                requiredString(body, "uploadId"),
                requiredString(body, "fileName"),
                requiredInt(body, "totalChunks"),
                requiredLong(body, "fileSize"),
                subjectId,
                sessionId,
                optionalString(body, "platform"),
                optionalString(body, "referenceGenome"),
                optionalString(body, "sampleType"));
        return ApiResponse.ok(Map.of(
                "status", "merged",
                "sampleId", saved.getId(),
                "fileName", saved.getVcfFileName()));
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value.toString();
    }

    private static String optionalString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private static int requiredInt(Map<String, Object> body, String key) {
        return Math.toIntExact(requiredLong(body, key));
    }

    private static long requiredLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null) throw new IllegalArgumentException(key + " 不能为空");
        return Long.parseLong(value.toString());
    }

    private static Long optionalLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(value.toString());
    }

    @PostMapping("/samples/{sampleId}/parse")
    public ApiResponse<Map<String, Object>> triggerParsing(@PathVariable Long sampleId) {
        scopeGuard.assertSubjectAccess(requireSample(sampleId).getSubjectId());
        return ApiResponse.ok(service.parseVcf(sampleId));
    }

    @GetMapping("/samples/{sampleId}/variant-summary")
    public ApiResponse<Map<String, Object>> getVariantSummary(@PathVariable Long sampleId) {
        scopeGuard.assertSubjectAccess(requireSample(sampleId).getSubjectId());
        return ApiResponse.ok(service.getVariantSummary(sampleId));
    }

    @GetMapping("/platforms")
    public ApiResponse<List<Map<String, Object>>> getPlatforms() {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT id,code AS name,name AS label FROM genetics_platform WHERE is_active=1 ORDER BY sort_order,id"));
    }

    @GetMapping("/reference-genomes")
    public ApiResponse<List<Map<String, Object>>> getReferenceGenomes() {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT id,code AS name,name AS label FROM reference_genome WHERE is_active=1 ORDER BY sort_order,id"));
    }

    @GetMapping("/dynamic-fields")
    public ApiResponse<List<Map<String, Object>>> dynamicFields() {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT fd.id,fd.field_code AS fieldCode,fd.label,fd.description," +
            "fd.field_type AS fieldType,fd.unit,fd.default_value AS defaultValue," +
            "fd.options_json AS optionsJson,fd.required_flag AS requiredFlag," +
            "fd.sort_order AS sortOrder,f.version " +
            "FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id " +
            "WHERE f.module='GENETICS' AND f.status='PUBLISHED' AND fd.status='PUBLISHED' " +
            "ORDER BY fd.sort_order,fd.id"));
    }

    @GetMapping("/samples/{sampleId}/dynamic-values")
    public ApiResponse<List<Map<String, Object>>> dynamicValues(@PathVariable Long sampleId) {
        scopeGuard.assertSubjectAccess(requireSample(sampleId).getSubjectId());
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT fv.field_id AS fieldId,fd.field_code AS fieldCode,fv.value_json AS valueJson " +
            "FROM field_value fv JOIN field_definition fd ON fd.id=fv.field_id " +
            "WHERE fv.entity_type='GENETICS_SAMPLE' AND fv.entity_id=?", sampleId));
    }

    @PutMapping("/samples/{sampleId}/dynamic-values")
    public ApiResponse<Map<String, String>> saveDynamicValues(
            @PathVariable Long sampleId, @RequestBody Map<String, Object> values,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        GeneticsSample sample = requireSample(sampleId);
        scopeGuard.assertSubjectAccess(sample.getSubjectId());
        Map<String, Map<String, Object>> definitions = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT fd.id,fd.field_code AS fieldCode,fd.required_flag AS requiredFlag,f.version " +
                "FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id " +
                "WHERE f.module='GENETICS' AND f.status='PUBLISHED' AND fd.status='PUBLISHED'")) {
            definitions.put(String.valueOf(row.get("fieldCode")), row);
        }
        for (Map.Entry<String, Map<String, Object>> entry : definitions.entrySet()) {
            Object value = values.get(entry.getKey());
            Object required = entry.getValue().get("requiredFlag");
            boolean requiredFlag = required instanceof Boolean flag ? flag
                    : required instanceof Number number && number.intValue() != 0;
            if (requiredFlag
                    && (value == null || value.toString().isBlank())) {
                throw new IllegalArgumentException(entry.getKey() + " 为必填字段");
            }
            if (value == null) continue;
            String json;
            try { json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
            catch (Exception e) { throw new IllegalArgumentException("字段值格式错误"); }
            jdbc.update(
                "INSERT INTO field_value(field_id,entity_type,entity_id,value_json,form_version,created_by) " +
                "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE value_json=VALUES(value_json)," +
                "form_version=VALUES(form_version),updated_at=NOW()",
                entry.getValue().get("id"), "GENETICS_SAMPLE", sampleId, json,
                entry.getValue().get("version"), userId);
        }
        return ApiResponse.ok(Map.of("message", "Dynamic values saved"));
    }

    @GetMapping("/samples/{sampleId}/variants")
    public ApiResponse<PageResult<GeneticsVariant>> listVariants(@PathVariable Long sampleId,
            @RequestParam(required = false) String geneSymbol,
            @RequestParam(required = false) String variantType,
            @RequestParam(required = false) String clinicalSignificance,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        scopeGuard.assertSubjectAccess(requireSample(sampleId).getSubjectId());
        return ApiResponse.ok(service.listVariants(
                sampleId, geneSymbol, variantType, clinicalSignificance, page, size));
    }

    @GetMapping("/samples/{sampleId}/variants/{variantId}")
    public ApiResponse<GeneticsVariant> getVariant(@PathVariable Long sampleId, @PathVariable Long variantId) {
        scopeGuard.assertSubjectAccess(requireSample(sampleId).getSubjectId());
        return ApiResponse.ok(service.getVariant(sampleId, variantId));
    }

    @GetMapping("/variants")
    public ApiResponse<Map<String, Object>> getVariantsBySubject(
            @RequestParam Long subjectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(service.getVariantsBySubject(subjectId, page, size));
    }

    private GeneticsSample requireSample(Long id) {
        GeneticsSample sample = service.getSample(id);
        if (sample == null) throw new IllegalArgumentException("遗传样本不存在");
        return sample;
    }
}
