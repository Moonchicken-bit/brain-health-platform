package com.brainhealth.subject.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.subject.dto.ImportResult;
import com.brainhealth.subject.dto.SessionDTO;
import com.brainhealth.subject.dto.SubjectCreateRequest;
import com.brainhealth.subject.dto.SubjectDTO;
import com.brainhealth.subject.dto.SubjectUpdateRequest;
import com.brainhealth.subject.dto.TimelineItemDTO;
import com.brainhealth.subject.service.SubjectService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    /**
     * Paged search / list subjects with optional filters.
     */
    @GetMapping
    public ApiResponse<PageResult<SubjectDTO>> search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String ethnicity,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = "X-Institution-Id", required = false) Long callerInstitutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String callerProjectIds,
            @RequestHeader(value = "X-Roles", required = false) String callerRoles) {
        boolean admin = hasAdminRole(callerRoles);
        Set<Long> projectScope = admin ? null : parseIds(callerProjectIds);
        if (!admin && projectId != null && !projectScope.contains(projectId)) {
            throw new IllegalArgumentException("无权访问该项目的受试者");
        }
        Long institutionScope = admin ? institutionId : callerInstitutionId;
        if (!admin && institutionId != null && !institutionId.equals(callerInstitutionId)) {
            throw new IllegalArgumentException("无权访问该机构的受试者");
        }
        PageResult<SubjectDTO> result = subjectService.search(
                page, size, subjectId, sex, ethnicity,
                institutionScope, projectId, isActive, projectScope);
        return ApiResponse.ok(result);
    }

    /**
     * Get a single subject by its database primary key.
     */
    @GetMapping("/{id}")
    public ApiResponse<SubjectDTO> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        SubjectDTO subject = subjectService.getById(id);
        assertScope(subject, institutionId, projectIds, roles);
        return ApiResponse.ok(subject);
    }

    private static Set<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : raw.split(",")) {
            if (!value.isBlank()) ids.add(Long.valueOf(value.trim()));
        }
        return ids;
    }

    private static boolean hasAdminRole(String raw) {
        if (raw == null) return false;
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .anyMatch(role -> role.equalsIgnoreCase("SYSTEM_ADMIN") || role.equalsIgnoreCase("ADMIN"));
    }

    private static void assertScope(SubjectDTO subject, Long institutionId, String projectIds, String roles) {
        if (hasAdminRole(roles)) return;
        if (institutionId == null || !institutionId.equals(subject.getInstitutionId())
                || !parseIds(projectIds).contains(subject.getProjectId())) {
            throw new IllegalArgumentException("无权访问该受试者");
        }
    }

    /**
     * Create a new subject.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubjectDTO> create(
            @Valid @RequestBody SubjectCreateRequest request,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!hasAdminRole(roles) && (institutionId == null
                || !institutionId.equals(request.getInstitutionId())
                || !parseIds(projectIds).contains(request.getProjectId()))) {
            throw new IllegalArgumentException("无权在该机构或项目登记受试者");
        }
        SubjectDTO created = subjectService.create(request);
        return ApiResponse.created(created);
    }

    /**
     * Update an existing subject (partial update).
     */
    @PutMapping("/{id}")
    public ApiResponse<SubjectDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SubjectUpdateRequest request,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertScope(subjectService.getById(id), institutionId, projectIds, roles);
        SubjectDTO updated = subjectService.update(id, request);
        return ApiResponse.ok(updated);
    }

    /**
     * Soft-delete a subject (sets is_active = false).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertScope(subjectService.getById(id), institutionId, projectIds, roles);
        subjectService.softDelete(id);
    }

    /**
     * Batch-import subjects from a CSV or Excel file.
     */
    @PostMapping("/batch-import")
    public ApiResponse<ImportResult> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!hasAdminRole(roles)) throw new IllegalArgumentException("仅管理员可以批量导入受试者");
        ImportResult result = subjectService.batchImport(file);
        return ApiResponse.ok(result);
    }

    /**
     * Get timeline events for a subject (registrations, sessions, assessments, etc.).
     */
    @GetMapping("/{id}/timeline")
    public ApiResponse<List<TimelineItemDTO>> getTimeline(
            @PathVariable Long id,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertScope(subjectService.getById(id), institutionId, projectIds, roles);
        List<TimelineItemDTO> timeline = subjectService.getTimeline(id);
        return ApiResponse.ok(timeline);
    }

    /**
     * List all active sessions for a subject.
     */
    @GetMapping("/{id}/sessions")
    public ApiResponse<List<SessionDTO>> getSessions(
            @PathVariable Long id,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertScope(subjectService.getById(id), institutionId, projectIds, roles);
        List<SessionDTO> sessions = subjectService.getSessions(id);
        return ApiResponse.ok(sessions);
    }

    /**
     * Copy the most recent session of a subject as a new session.
     */
    @PostMapping("/{id}/copy-last-session")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionDTO> copyLastSession(
            @PathVariable Long id,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertScope(subjectService.getById(id), institutionId, projectIds, roles);
        SessionDTO session = subjectService.copyLastSession(id);
        return ApiResponse.created(session);
    }
}
