package com.brainhealth.scale.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.scale.dto.VisitAttachmentDTO;
import com.brainhealth.scale.entity.VisitAttachment;
import com.brainhealth.scale.service.VisitAttachmentService;
import com.brainhealth.scale.service.AttachmentTextAnalysisService;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/scales/attachments")
public class VisitAttachmentController {
    private final VisitAttachmentService service;
    private final AttachmentTextAnalysisService analysisService;
    private final DataScopeGuard scopeGuard;

    public VisitAttachmentController(VisitAttachmentService service,
                                     AttachmentTextAnalysisService analysisService,
                                     DataScopeGuard scopeGuard) {
        this.service = service;
        this.analysisService = analysisService;
        this.scopeGuard = scopeGuard;
    }

    @PostMapping
    public ApiResponse<VisitAttachmentDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long subjectId,
            @RequestParam String visitCode,
            @RequestParam String fieldCode) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.created(service.store(file, subjectId, visitCode, fieldCode));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        VisitAttachment attachment = service.get(id);
        scopeGuard.assertSubjectAccess(attachment.getSubjectId());
        Resource resource = service.loadContent(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @GetMapping("/{id}")
    public ApiResponse<VisitAttachmentDTO> metadata(@PathVariable String id) {
        VisitAttachment attachment = service.get(id);
        scopeGuard.assertSubjectAccess(attachment.getSubjectId());
        return ApiResponse.ok(VisitAttachmentDTO.from(attachment));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        scopeGuard.assertSubjectAccess(service.get(id).getSubjectId());
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/analyze-text")
    public ApiResponse<AttachmentTextAnalysisService.AnalysisResult> analyzeText(@PathVariable String id) {
        scopeGuard.assertSubjectAccess(service.get(id).getSubjectId());
        return ApiResponse.ok(analysisService.analyze(id));
    }
}
