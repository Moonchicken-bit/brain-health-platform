package com.brainhealth.imaging.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.imaging.entity.*;
import com.brainhealth.imaging.dto.ArchiveImportRequest;
import com.brainhealth.common.security.DataScopeGuard;
import com.brainhealth.imaging.service.ImagingService;
import com.brainhealth.imaging.service.ArchiveInspectionService;
import com.brainhealth.imaging.service.ArchiveAnalysisService;
import com.brainhealth.imaging.service.ArchiveAnalysisTaskService;
import com.brainhealth.imaging.service.ImagingProcessingService;
import com.brainhealth.imaging.service.DicomMetadataService;
import com.brainhealth.imaging.service.PreviewBackfillService;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/imaging")
public class ImagingController {
    private final ImagingService service;
    private final MinioClient minio;
    private final ArchiveInspectionService archiveInspectionService;
    private final ArchiveAnalysisService archiveAnalysisService;
    private final ArchiveAnalysisTaskService archiveTaskService;
    private final DataScopeGuard scopeGuard;
    private final ImagingProcessingService processingService;
    private final DicomMetadataService dicomMetadataService;
    private final PreviewBackfillService previewBackfillService;
    private final JdbcTemplate jdbc;
    private static final String BUCKET = "brain-health-imaging";
    private static final Pattern SAFE_UPLOAD_ID = Pattern.compile("[A-Za-z0-9-]{8,80}");
    private final Path chunkRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImagingController(
            ImagingService service,
            MinioClient minio,
            ArchiveInspectionService archiveInspectionService,
            ArchiveAnalysisService archiveAnalysisService,
            ArchiveAnalysisTaskService archiveTaskService,
            ImagingProcessingService processingService,
            DicomMetadataService dicomMetadataService,
            PreviewBackfillService previewBackfillService,
            JdbcTemplate jdbc,
            DataScopeGuard scopeGuard,
            @Value("${brain-health.imaging.chunk-root:${java.io.tmpdir}/brain-health/imaging-chunks}") String chunkRoot) {
        this.service = service;
        this.minio = minio;
        this.archiveInspectionService = archiveInspectionService;
        this.archiveAnalysisService = archiveAnalysisService;
        this.archiveTaskService = archiveTaskService;
        this.processingService = processingService;
        this.dicomMetadataService = dicomMetadataService;
        this.previewBackfillService = previewBackfillService;
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
        this.chunkRoot = Paths.get(chunkRoot).toAbsolutePath().normalize();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
            if (!exists) minio.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        } catch (Exception e) { /* bucket creation failed, will retry on upload */ }
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResult<ImagingSession>> listSessions(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long modalityId,
            @RequestParam(required = false) String qcStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (subjectId != null) scopeGuard.assertSubjectAccess(subjectId);
        else if (!scopeGuard.currentScope().admin()) {
            return ApiResponse.ok(service.listSessionsForSubjects(scopeGuard.accessibleSubjectIds(), page, size));
        }
        return ApiResponse.ok(service.listSessions(subjectId, modalityId, qcStatus, page, size));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<ImagingSession> getSession(@PathVariable Long id) {
        ImagingSession session = requireSession(id);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        return ApiResponse.ok(session);
    }

    @GetMapping("/sessions/{sessionId}/series")
    public ApiResponse<List<ImagingSeries>> getSeries(@PathVariable Long sessionId) {
        ImagingSession session = requireSession(sessionId);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        return ApiResponse.ok(service.getSeries(sessionId));
    }

    @GetMapping("/series/{id}")
    public ApiResponse<ImagingSeries> getSeriesDetail(@PathVariable Long id) {
        ImagingSeries series = requireSeries(id);
        scopeGuard.assertSubjectAccess(requireSession(series.getImagingSessionId()).getSubjectId());
        return ApiResponse.ok(series);
    }

    @PostMapping("/series/{seriesId}/qc")
    public ApiResponse<ImagingSeries> updateQC(@PathVariable Long seriesId,
            @RequestBody Map<String, String> body) {
        ImagingSeries series = requireSeries(seriesId);
        scopeGuard.assertSubjectAccess(requireSession(series.getImagingSessionId()).getSubjectId());
        return ApiResponse.ok(service.updateQC(seriesId, body.get("qcStatus"), body.get("qcNotes")));
    }

    @PostMapping("/preprocessing")
    public ApiResponse<ImagingProcessingService.ProcessingTask> submitPreprocessing(
            @RequestBody Map<String, Object> body) {
        Long sessionId = requiredLong(body, "imagingSessionId");
        ImagingSession session = requireSession(sessionId);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        Object pipeline = body.get("pipelineId");
        if (pipeline == null) throw new IllegalArgumentException("缺少 pipelineId");
        return ApiResponse.ok(processingService.submitPreprocessing(sessionId, pipeline.toString()));
    }

    @GetMapping("/preprocessing/{jobId}")
    public ApiResponse<ImagingProcessingService.ProcessingTask> getPreprocessingStatus(
            @PathVariable String jobId) {
        ImagingProcessingService.ProcessingTask task = processingService.get(jobId);
        scopeGuard.assertSubjectAccess(task.subjectId());
        return ApiResponse.ok(task);
    }

    @GetMapping("/processing/tools")
    public ApiResponse<Map<String, Boolean>> processingTools() {
        return ApiResponse.ok(processingService.toolAvailability());
    }

    @PostMapping("/convert-to-bids")
    public ApiResponse<ImagingProcessingService.ProcessingTask> convertToBIDS(
            @RequestBody Map<String, Object> body) {
        Long sessionId = requiredLong(body, "imagingSessionId");
        ImagingSession session = requireSession(sessionId);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        return ApiResponse.ok(processingService.submitBids(sessionId));
    }

    @GetMapping("/bids/{subjectId}/{sessionLabel}")
    public ApiResponse<List<Map<String, String>>> browseBIDS(
            @PathVariable Long subjectId, @PathVariable String sessionLabel) {
        scopeGuard.assertSubjectAccess(subjectId);
        return ApiResponse.ok(processingService.browseBids(subjectId, sessionLabel));
    }

    @GetMapping("/modalities")
    public ApiResponse<List<Map<String, Object>>> getModalities() {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT id,code,COALESCE(name_zh,name) AS name,category FROM imaging_modality " +
                "WHERE is_active=1 ORDER BY sort_order,id"));
    }

    @GetMapping("/dynamic-fields")
    public ApiResponse<List<Map<String, Object>>> dynamicFields() {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT fd.id,fd.field_code AS fieldCode,fd.label,fd.description,
                   fd.field_type AS fieldType,fd.unit,fd.default_value AS defaultValue,
                   fd.options_json AS options,fd.validation_json AS validation,
                   fd.required_flag AS requiredFlag,fd.sort_order AS sortOrder,f.version
            FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id
            WHERE f.module='IMAGING' AND f.status='PUBLISHED' AND fd.status='PUBLISHED'
            ORDER BY fd.sort_order,fd.id
            """));
    }

    @GetMapping("/sessions/{id}/dynamic-values")
    public ApiResponse<Map<String, Object>> imagingDynamicValues(@PathVariable Long id) {
        ImagingSession session = requireSession(id);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        return ApiResponse.ok(loadDynamicValues("IMAGING_SESSION", id));
    }

    @PutMapping("/sessions/{id}/dynamic-values")
    public ApiResponse<Map<String, String>> saveImagingDynamicValues(
            @PathVariable Long id, @RequestBody Map<String, Object> values,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        ImagingSession session = requireSession(id);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        saveDynamicValues("IMAGING", "IMAGING_SESSION", id, values, userId);
        return ApiResponse.ok(Map.of("message", "影像扩展字段已保存"));
    }

    private Map<String, Object> loadDynamicValues(String entityType, Long entityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
            SELECT fd.field_code,fv.value_json
            FROM field_value fv JOIN field_definition fd ON fd.id=fv.field_id
            WHERE fv.entity_type=? AND fv.entity_id=?
            """, entityType, entityId)) {
            Object raw = row.get("value_json");
            try {
                result.put(String.valueOf(row.get("field_code")),
                    raw == null ? null : objectMapper.readValue(raw.toString(), Object.class));
            } catch (Exception ignored) { result.put(String.valueOf(row.get("field_code")), raw); }
        }
        return result;
    }

    private void saveDynamicValues(String module, String entityType, Long entityId,
                                   Map<String, Object> values, Long userId) {
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
            SELECT fd.id,fd.field_code,f.version
            FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id
            WHERE f.module=? AND f.status='PUBLISHED' AND fd.status='PUBLISHED'
            """, module)) fields.put(String.valueOf(row.get("field_code")), row);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Map<String, Object> field = fields.get(entry.getKey());
            if (field == null) continue;
            try {
                String json = objectMapper.writeValueAsString(entry.getValue());
                jdbc.update("""
                    INSERT INTO field_value(field_id,entity_type,entity_id,value_json,form_version,created_by)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE value_json=VALUES(value_json),updated_at=NOW()
                    """, field.get("id"), entityType, entityId, json, field.get("version"), userId);
            } catch (Exception ex) { throw new IllegalArgumentException("扩展字段值格式错误", ex); }
        }
    }

    @GetMapping("/sessions/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadSession(@PathVariable Long id) {
        try {
            ImagingSession session = requireSession(id);
            scopeGuard.assertSubjectAccess(session.getSubjectId());
            List<ImagingSeries> seriesList = service.getSeries(id);
            StreamingResponseBody body = output -> {
                try (var zipOut = new java.util.zip.ZipOutputStream(output)) {
                    Set<String> addedObjects = new HashSet<>();
                    for (ImagingSeries series : seriesList) {
                        String objectName = series.getFilePath();
                        if (objectName == null || objectName.isBlank() || !addedObjects.add(objectName)) continue;
                        try (var stream = minio.getObject(
                                GetObjectArgs.builder().bucket(BUCKET).object(objectName).build())) {
                            String entryName = Paths.get(objectName).getFileName().toString();
                            zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName));
                            stream.transferTo(zipOut);
                            zipOut.closeEntry();
                        } catch (Exception ignored) { /* skip missing files */ }
                    }
                }
            };
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=imaging_session_" + id + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/series/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadSeries(@PathVariable Long id) {
        try {
            ImagingSeries series = requireSeries(id);
            scopeGuard.assertSubjectAccess(requireSession(series.getImagingSessionId()).getSubjectId());
            String objectName = series.getFilePath();
            if (objectName == null || objectName.isBlank()) {
                throw new FileNotFoundException("该序列未关联原始影像文件");
            }
            StreamingResponseBody body = output -> {
                try {
                    try (var stream = minio.getObject(
                            GetObjectArgs.builder().bucket(BUCKET).object(objectName).build())) {
                        stream.transferTo(output);
                    }
                } catch (Exception error) {
                    throw new IOException("影像文件读取失败", error);
                }
            };
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFileName(Paths.get(objectName).getFileName().toString()) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/series/{id}/preview")
    public ResponseEntity<byte[]> getPreview(@PathVariable Long id) {
        try {
            ImagingSeries series = requireSeries(id);
            scopeGuard.assertSubjectAccess(requireSession(series.getImagingSessionId()).getSubjectId());
            String objectName = "series/" + series.getId() + "/preview.png";
            try (var stream = minio.getObject(GetObjectArgs.builder().bucket(BUCKET).object(objectName).build())) {
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(stream.readAllBytes());
            }
        } catch (Exception e) {
            return ResponseEntity.status(404).body(null);
        }
    }

    @PostMapping("/sessions/{id}/previews/regenerate")
    public ApiResponse<Map<String, Object>> regeneratePreviews(@PathVariable Long id) {
        ImagingSession session = requireSession(id);
        scopeGuard.assertSubjectAccess(session.getSubjectId());
        return ApiResponse.ok(previewBackfillService.regenerate(id));
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long modalityId) {
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        int uploaded = 0;
        List<String> errors = new ArrayList<>();
        List<ArchiveInspectionService.InspectionSummary> inspections = new ArrayList<>();
        List<String> objectNames = new ArrayList<>();
        List<String> analysisTaskIds = new ArrayList<>();
        List<Long> importedSeriesIds = new ArrayList<>();
        for (MultipartFile file : files) {
            Path staged = null;
            try {
                if (file.isEmpty()) throw new IllegalArgumentException("文件为空");
                String safeName = safeFileName(file.getOriginalFilename());
                InputStream source;
                long sourceSize;
                if (isArchive(safeName)) {
                    Files.createDirectories(chunkRoot);
                    staged = Files.createTempFile(chunkRoot, "archive-", ".tmp");
                    try (InputStream input = file.getInputStream()) {
                        Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
                    }
                    inspections.add(archiveInspectionService.inspect(staged, safeName));
                    source = Files.newInputStream(staged);
                    sourceSize = Files.size(staged);
                } else {
                    Files.createDirectories(chunkRoot);
                    staged = Files.createTempFile(chunkRoot, "dicom-", ".bin");
                    try (InputStream input = file.getInputStream()) {
                        Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
                    }
                    source = Files.newInputStream(staged);
                    sourceSize = Files.size(staged);
                }
                String objectName = sessionId != null
                    ? "session/" + sessionId + "/" + UUID.randomUUID() + "-" + safeName
                    : "uploads/" + UUID.randomUUID() + "/" + safeName;
                try (source) {
                    minio.putObject(PutObjectArgs.builder()
                        .bucket(BUCKET).object(objectName)
                        .stream(source, sourceSize, -1)
                        .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        .build());
                }
                uploaded++;
                objectNames.add(objectName);
                if (isArchive(safeName)) {
                    analysisTaskIds.add(archiveTaskService.submit(staged, safeName, objectName));
                }
                if (!isArchive(safeName) && sessionId != null && modalityId != null) {
                    Long subjectId = jdbc.queryForObject(
                            "SELECT subject_id FROM `session` WHERE id=?", Long.class, sessionId);
                    if (subjectId == null) throw new IllegalArgumentException("访视不存在");
                    DicomMetadataService.DicomMetadata metadata = dicomMetadataService.read(staged);
                    importedSeriesIds.add(service.importDirectDicom(
                            sessionId, subjectId, modalityId, objectName, metadata).getId());
                }
            } catch (Exception e) {
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            } finally {
                if (staged != null) try { Files.deleteIfExists(staged); } catch (IOException ignored) { }
            }
        }
        if (uploaded == 0 && !errors.isEmpty()) {
            throw new IllegalArgumentException("影像上传失败：" + String.join("；", errors));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", errors.isEmpty() ? "completed" : "partial");
        result.put("uploaded", uploaded);
        result.put("errors", errors);
        result.put("archiveInspections", inspections);
        result.put("objectNames", objectNames);
        result.put("analysisTaskIds", analysisTaskIds);
        result.put("importedSeriesIds", importedSeriesIds);
        return ApiResponse.ok(result);
    }

    @PostMapping("/archive/analyze")
    public ApiResponse<ArchiveAnalysisTaskService.TaskSnapshot> analyzeArchive(
            @RequestParam("file") MultipartFile file) {
        Path staged = null;
        Path extracted = null;
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("压缩包不能为空");
            String safeName = safeFileName(file.getOriginalFilename());
            if (!isArchive(safeName)) throw new IllegalArgumentException("仅支持 ZIP 或 RAR 压缩包");
            Files.createDirectories(chunkRoot);
            staged = Files.createTempFile(chunkRoot, "analysis-", ".archive");
            extracted = Files.createTempDirectory(chunkRoot, "analysis-files-");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            String taskId = archiveTaskService.submit(staged, safeName, null);
            return ApiResponse.ok(archiveTaskService.get(taskId));
        } catch (Exception e) {
            throw new IllegalArgumentException("压缩包分析失败：" + e.getMessage(), e);
        } finally {
            if (staged != null) try { Files.deleteIfExists(staged); } catch (IOException ignored) { }
            ArchiveAnalysisService.deleteTree(extracted);
        }
    }

    @GetMapping("/archive/tasks/{taskId}")
    public ApiResponse<ArchiveAnalysisTaskService.TaskSnapshot> getArchiveTask(@PathVariable String taskId) {
        return ApiResponse.ok(archiveTaskService.get(taskId));
    }

    @PostMapping("/archive/confirm")
    public ApiResponse<ImagingSession> confirmArchiveImport(@RequestBody ArchiveImportRequest request) {
        scopeGuard.assertSubjectAccess(request.getSubjectId());
        scopeGuard.assertSessionAccess(request.getSessionId());
        ImagingSession session = service.confirmArchiveImport(request);
        Map<String, ArchiveImportRequest.Series> requested = new HashMap<>();
        for (ArchiveImportRequest.Series item : request.getSeries()) {
            if (item.getSeriesInstanceUid() != null) requested.put(item.getSeriesInstanceUid(), item);
        }
        for (ImagingSeries saved : service.getSeries(session.getId())) {
            ArchiveImportRequest.Series source = requested.get(saved.getSeriesUid());
            if (source == null || source.getPreviewBase64() == null || source.getPreviewBase64().isBlank()) continue;
            try {
                byte[] png = Base64.getDecoder().decode(source.getPreviewBase64());
                minio.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object("series/" + saved.getId() + "/preview.png")
                    .stream(new ByteArrayInputStream(png), png.length, -1)
                    .contentType(MediaType.IMAGE_PNG_VALUE)
                    .build());
            } catch (Exception error) {
                throw new IllegalStateException("影像序列预览保存失败: " + saved.getSeriesDescription(), error);
            }
        }
        return ApiResponse.created(session);
    }

    @PostMapping("/upload/chunk")
    public ApiResponse<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileSize") long fileSize) {
        try {
            validateChunkRequest(uploadId, chunkIndex, totalChunks, fileSize);
            if (chunk.isEmpty() || chunk.getSize() > 25L * 1024 * 1024) {
                throw new IllegalArgumentException("分片为空或超过 25MB");
            }
            Path dir = uploadDirectory(uploadId);
            Files.createDirectories(dir);
            Path chunkFile = dir.resolve(String.format("chunk_%05d", chunkIndex));
            try (InputStream in = chunk.getInputStream()) {
                Files.copy(in, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return ApiResponse.ok(Map.of("uploadId", uploadId, "chunk", chunkIndex, "status", "ok"));
        } catch (Exception e) {
            throw new IllegalArgumentException("影像分片上传失败：" + e.getMessage(), e);
        }
    }

    @PostMapping("/upload/merge")
    public ApiResponse<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body) {
        String uploadId = (String) body.get("uploadId");
        String fileName = (String) body.getOrDefault("fileName", "merged.dcm");
        Long sessionId = body.get("sessionId") instanceof Number n ? n.longValue() : null;
        if (sessionId != null) scopeGuard.assertSessionAccess(sessionId);
        int totalChunks = requiredInt(body, "totalChunks");
        long fileSize = requiredLong(body, "fileSize");
        Path merged = null;
        try {
            validateChunkRequest(uploadId, 0, totalChunks, fileSize);
            Path dir = uploadDirectory(uploadId);
            Files.createDirectories(chunkRoot);
            merged = Files.createTempFile(chunkRoot, "merged-", ".tmp");
            try (var out = Files.newOutputStream(merged)) {
                for (int index = 0; index < totalChunks; index++) {
                    Path chunkPath = dir.resolve(String.format("chunk_%05d", index));
                    if (!Files.isRegularFile(chunkPath)) {
                        throw new IllegalArgumentException("缺少分片 " + index);
                    }
                    Files.copy(chunkPath, out);
                }
            }
            long actualSize = Files.size(merged);
            if (actualSize != fileSize) {
                throw new IllegalArgumentException("文件大小校验失败，期望 " + fileSize + "，实际 " + actualSize);
            }
            String safeName = safeFileName(fileName);
            ArchiveInspectionService.InspectionSummary inspection =
                isArchive(safeName) ? archiveInspectionService.inspect(merged, safeName) : null;
            String objectName = sessionId != null
                ? "session/" + sessionId + "/" + uploadId + "-" + safeName
                : "uploads/" + uploadId + "/" + safeName;
            minio.putObject(PutObjectArgs.builder()
                .bucket(BUCKET).object(objectName)
                .stream(Files.newInputStream(merged), actualSize, -1)
                .contentType("application/octet-stream")
                .build());
            String analysisTaskId =
                isArchive(safeName) ? archiveTaskService.submit(merged, safeName, objectName) : null;
            deleteDirectory(dir);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "merged");
            result.put("uploadId", uploadId);
            result.put("objectName", objectName);
            if (inspection != null) result.put("archiveInspection", inspection);
            if (analysisTaskId != null) result.put("analysisTaskId", analysisTaskId);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            throw new IllegalArgumentException("影像分片合并失败：" + e.getMessage(), e);
        } finally {
            if (merged != null) {
                try { Files.deleteIfExists(merged); } catch (IOException ignored) { }
            }
        }
    }

    private Path uploadDirectory(String uploadId) {
        if (uploadId == null || !SAFE_UPLOAD_ID.matcher(uploadId).matches()) {
            throw new IllegalArgumentException("无效的上传 ID");
        }
        Path resolved = chunkRoot.resolve(uploadId).normalize();
        if (!resolved.startsWith(chunkRoot)) throw new IllegalArgumentException("无效的上传路径");
        return resolved;
    }

    private void validateChunkRequest(String uploadId, int chunkIndex, int totalChunks, long fileSize) {
        uploadDirectory(uploadId);
        if (totalChunks < 1 || totalChunks > 100000) throw new IllegalArgumentException("分片总数无效");
        if (chunkIndex < 0 || chunkIndex >= totalChunks) throw new IllegalArgumentException("分片序号无效");
        if (fileSize < 1 || fileSize > 20L * 1024 * 1024 * 1024) throw new IllegalArgumentException("文件大小无效");
    }

    private static String safeFileName(String original) {
        String name = original == null ? "upload.bin" : Paths.get(original).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isBlank() || name.length() > 180) {
            name = name.isBlank() ? "upload.bin" : name.substring(name.length() - 180);
        }
        return name;
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".rar");
    }

    private static int requiredInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("缺少参数 " + key);
        return number.intValue();
    }

    private static long requiredLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("缺少参数 " + key);
        return number.longValue();
    }

    private ArchiveAnalysisService.ArchiveAnalysis analyzeStagedArchive(Path archive, String fileName)
            throws IOException {
        Path extracted = Files.createTempDirectory(chunkRoot, "archive-analysis-");
        try {
            return archiveAnalysisService.analyze(archive, fileName, extracted);
        } finally {
            ArchiveAnalysisService.deleteTree(extracted);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        }
    }

    private ImagingSession requireSession(Long id) {
        ImagingSession session = service.getSession(id);
        if (session == null) throw new IllegalArgumentException("影像检查不存在");
        return session;
    }

    private ImagingSeries requireSeries(Long id) {
        ImagingSeries series = service.getSeriesDetail(id);
        if (series == null) throw new IllegalArgumentException("影像序列不存在");
        return series;
    }
}
