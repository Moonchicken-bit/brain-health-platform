package com.brainhealth.subject.controller;

import com.brainhealth.common.constant.Constants;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.security.DataScopeGuard;
import com.brainhealth.subject.service.UnifiedImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/unified-imports")
public class UnifiedImportController {
    private final UnifiedImportService service;
    private final DataScopeGuard scopeGuard;

    public UnifiedImportController(UnifiedImportService service, DataScopeGuard scopeGuard) {
        this.service = service;
        this.scopeGuard = scopeGuard;
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @PathVariable Long sessionId,
            @RequestParam Long subjectId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) throws Exception {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.created(service.analyze(file, subjectId, sessionId, userId));
    }

    @PostMapping("/chunked")
    public ApiResponse<Map<String, Object>> initializeChunkUpload(
            @PathVariable Long sessionId, @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) throws Exception {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        Long subjectId = Long.valueOf(String.valueOf(body.get("subjectId")));
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.created(service.initializeChunkUpload(subjectId, sessionId,
            String.valueOf(body.get("fileName")),
            Long.parseLong(String.valueOf(body.get("fileSize"))),
            Integer.parseInt(String.valueOf(body.get("totalChunks"))), userId));
    }

    @GetMapping("/chunked/{uploadId}")
    public ApiResponse<Map<String, Object>> chunkStatus(
            @PathVariable Long sessionId, @PathVariable String uploadId,
            @RequestParam Long subjectId) throws Exception {
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(service.chunkStatus(uploadId, subjectId, sessionId));
    }

    @PutMapping("/chunked/{uploadId}/chunks/{chunkIndex}")
    public ApiResponse<Map<String, Object>> uploadChunk(
            @PathVariable Long sessionId, @PathVariable String uploadId,
            @PathVariable int chunkIndex, @RequestParam Long subjectId,
            @RequestParam("file") MultipartFile file) throws Exception {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(service.saveChunk(uploadId, subjectId, sessionId, chunkIndex, file));
    }

    @PostMapping("/chunked/{uploadId}/complete")
    public ApiResponse<Map<String, Object>> completeChunkUpload(
            @PathVariable Long sessionId, @PathVariable String uploadId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) throws Exception {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        Long subjectId = Long.valueOf(String.valueOf(body.get("subjectId")));
        scopeGuard.assertSubjectAccess(subjectId);
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.created(service.completeChunkUpload(uploadId, subjectId, sessionId, userId));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long sessionId) {
        scopeGuard.assertSessionAccess(sessionId);
        return ApiResponse.ok(service.list(sessionId));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable Long sessionId, @PathVariable Long batchId) {
        scopeGuard.assertSessionAccess(sessionId);
        service.assertBatchSession(batchId, sessionId);
        return ApiResponse.ok(service.detail(batchId));
    }

    @PostMapping("/{batchId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable Long sessionId, @PathVariable Long batchId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        scopeGuard.assertSessionAccess(sessionId);
        service.assertBatchSession(batchId, sessionId);
        Object raw = body.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = raw instanceof List<?> list
            ? (List<Map<String, Object>>) list : List.of();
        return ApiResponse.ok(service.confirm(batchId, items, userId, authorization));
    }

    @PostMapping("/{batchId}/retry")
    public ApiResponse<Map<String, Object>> retry(
            @PathVariable Long sessionId, @PathVariable Long batchId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI", "RESEARCHER");
        scopeGuard.assertSessionAccess(sessionId);
        service.assertBatchSession(batchId, sessionId);
        return ApiResponse.ok(service.retry(batchId, authorization));
    }
}
