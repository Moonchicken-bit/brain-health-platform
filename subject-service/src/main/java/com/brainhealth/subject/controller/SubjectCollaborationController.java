package com.brainhealth.subject.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.util.JwtUtil;
import com.brainhealth.subject.entity.SubjectBusinessTag;
import com.brainhealth.subject.entity.SubjectProjectNote;
import com.brainhealth.subject.service.SubjectCollaborationService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class SubjectCollaborationController {
    private final SubjectCollaborationService service;

    public SubjectCollaborationController(SubjectCollaborationService service) {
        this.service = service;
    }

    @GetMapping("/subjects/favorites")
    public ApiResponse<Set<Long>> favorites(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        return ApiResponse.ok(service.favorites(
            userId(authorization), institutionId, parseIds(projectIds), isAdmin(roles)));
    }

    @PutMapping("/subjects/{subjectId}/favorite")
    public ApiResponse<Map<String, Boolean>> setFavorite(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @PathVariable Long subjectId,
            @RequestBody Map<String, Boolean> body) {
        assertSubjectScope(subjectId, institutionId, projectIds, roles);
        boolean favorite = Boolean.TRUE.equals(body.get("favorite"));
        return ApiResponse.ok(Map.of("favorite", service.setFavorite(userId(authorization), subjectId, favorite)));
    }

    @GetMapping("/projects/{projectId}/subject-tags")
    public ApiResponse<List<SubjectBusinessTag>> listTags(
            @PathVariable Long projectId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertProjectScope(projectId, projectIds, roles);
        return ApiResponse.ok(service.listTags(projectId));
    }

    @PostMapping("/projects/{projectId}/subject-tags")
    public ApiResponse<SubjectBusinessTag> createTag(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body) {
        assertProjectScope(projectId, projectIds, roles);
        return ApiResponse.created(service.createTag(
            userId(authorization), projectId, body.get("name"), body.get("color")));
    }

    @GetMapping("/subjects/{subjectId}/tags")
    public ApiResponse<List<SubjectBusinessTag>> subjectTags(
            @PathVariable Long subjectId,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertSubjectScope(subjectId, institutionId, projectIds, roles);
        return ApiResponse.ok(service.subjectTags(subjectId));
    }

    @PutMapping("/subjects/{subjectId}/tags")
    public ApiResponse<List<SubjectBusinessTag>> setSubjectTags(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @PathVariable Long subjectId,
            @RequestBody TagSelection body) {
        assertSubjectScope(subjectId, institutionId, projectIds, roles);
        return ApiResponse.ok(service.setSubjectTags(userId(authorization), subjectId, body.tagIds()));
    }

    @GetMapping("/subjects/{subjectId}/project-notes")
    public ApiResponse<List<SubjectProjectNote>> noteHistory(
            @PathVariable Long subjectId,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        assertSubjectScope(subjectId, institutionId, projectIds, roles);
        return ApiResponse.ok(service.noteHistory(subjectId));
    }

    @PostMapping("/subjects/{subjectId}/project-notes")
    public ApiResponse<SubjectProjectNote> saveNote(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Institution-Id", required = false) Long institutionId,
            @RequestHeader(value = "X-Project-Ids", required = false) String projectIds,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @PathVariable Long subjectId,
            @RequestBody Map<String, String> body) {
        assertSubjectScope(subjectId, institutionId, projectIds, roles);
        return ApiResponse.created(service.saveNote(userId(authorization), subjectId, body.get("content")));
    }

    private static Long userId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("请先登录");
        }
        Long userId = JwtUtil.getUserId(authorization.substring(7));
        if (userId == null) throw new IllegalArgumentException("登录状态无效或已过期");
        return userId;
    }

    private void assertSubjectScope(Long subjectId, Long institutionId, String projectIds, String roles) {
        service.assertSubjectScope(subjectId, institutionId, parseIds(projectIds), isAdmin(roles));
    }

    private static void assertProjectScope(Long projectId, String projectIds, String roles) {
        if (!isAdmin(roles) && !parseIds(projectIds).contains(projectId)) {
            throw new IllegalArgumentException("无权访问该项目");
        }
    }

    private static Set<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        Arrays.stream(raw.split(",")).filter(value -> !value.isBlank())
            .map(String::trim).map(Long::valueOf).forEach(result::add);
        return result;
    }

    private static boolean isAdmin(String raw) {
        return raw != null && Arrays.stream(raw.split(",")).map(String::trim)
            .anyMatch(role -> role.equalsIgnoreCase("SYSTEM_ADMIN") || role.equalsIgnoreCase("ADMIN"));
    }

    public record TagSelection(List<Long> tagIds) { }
}
