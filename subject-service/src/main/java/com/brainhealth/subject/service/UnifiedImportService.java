package com.brainhealth.subject.service;

import com.brainhealth.common.util.JwtUtil;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class UnifiedImportService {
    private static final int MAX_FILES = 20000;
    private static final long MAX_UNCOMPRESSED = 50L * 1024 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final Path storageRoot;
    private final RestClient restClient = RestClient.create("http://127.0.0.1:8080");
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();

    public UnifiedImportService(JdbcTemplate jdbc,
            @Value("${unified-import.storage-root:./data/unified-imports}") String storageRoot) {
        this.jdbc = jdbc;
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, Object> analyze(MultipartFile file, Long subjectId, Long sessionId, Long userId)
            throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择综合压缩包");
        String original = safeName(file.getOriginalFilename());
        String lower = original.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".rar")) {
            throw new IllegalArgumentException("综合导入目前支持 ZIP 和 RAR 压缩包");
        }
        Integer visitExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM session WHERE id=? AND subject_id=?", Integer.class, sessionId, subjectId);
        if (visitExists == null || visitExists == 0) throw new IllegalArgumentException("访视与受试者不匹配");

        Files.createDirectories(storageRoot);
        Path staged = Files.createTempFile(storageRoot, "visit-package-", lower.endsWith(".rar") ? ".rar" : ".zip");
        file.transferTo(staged);
        return analyzeStaged(staged, original, subjectId, sessionId, userId);
    }

    private Map<String, Object> analyzeStaged(Path staged, String original, Long subjectId,
                                               Long sessionId, Long userId) throws Exception {
        String lower = original.toLowerCase(Locale.ROOT);
        String hash = sha256(staged);
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT id,status,storage_path FROM unified_import_batch WHERE subject_id=? AND session_id=? AND file_sha256=?",
            subjectId, sessionId, hash);
        if (!existing.isEmpty()) {
            Map<String, Object> previous = existing.get(0);
            Long previousId = ((Number) previous.get("id")).longValue();
            if (!"FAILED".equals(String.valueOf(previous.get("status")))) {
                Files.deleteIfExists(staged);
                return detail(previousId);
            }
            jdbc.update("DELETE FROM unified_import_job WHERE batch_id=?", previousId);
            jdbc.update("DELETE FROM unified_import_item WHERE batch_id=?", previousId);
            jdbc.update("DELETE FROM unified_import_batch WHERE id=?", previousId);
            Object previousPath = previous.get("storage_path");
            if (previousPath != null) {
                Path obsolete = Paths.get(previousPath.toString()).toAbsolutePath().normalize();
                if (obsolete.startsWith(storageRoot)) Files.deleteIfExists(obsolete);
            }
        }

        jdbc.update("INSERT INTO unified_import_batch(subject_id,session_id,original_file_name,file_sha256," +
                "storage_path,status,uploaded_by) VALUES (?,?,?,?,?,'ANALYZING',?)",
            subjectId, sessionId, original, hash, staged.toString(), userId);
        Long batchId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        try {
            List<Item> items = lower.endsWith(".rar") ? inspectRar(staged) : inspectZip(staged);
            for (Item item : items) {
                jdbc.update("INSERT INTO unified_import_item(batch_id,relative_path,file_name,file_size," +
                        "detected_module,confidence,included) VALUES (?,?,?,?,?,?,1)",
                    batchId, item.path(), item.name(), item.size(), item.module(), item.confidence());
            }
            Map<String, Long> counts = new LinkedHashMap<>();
            items.forEach(item -> counts.merge(item.module(), 1L, Long::sum));
            counts.forEach((module, count) -> jdbc.update(
                "INSERT INTO unified_import_job(batch_id,module,status,item_count) VALUES (?,?,'WAITING_CONFIRM',?)",
                batchId, module, count));
            jdbc.update("UPDATE unified_import_batch SET status='AWAITING_CONFIRMATION',total_files=?," +
                "included_files=? WHERE id=?", items.size(), items.size(), batchId);
            return detail(batchId);
        } catch (Exception e) {
            jdbc.update("UPDATE unified_import_batch SET status='FAILED',error_message=? WHERE id=?",
                truncate(e.getMessage()), batchId);
            throw e;
        }
    }

    public Map<String, Object> initializeChunkUpload(Long subjectId, Long sessionId, String fileName,
                                                      long fileSize, int totalChunks, Long userId)
            throws IOException {
        Integer visitExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM session WHERE id=? AND subject_id=?", Integer.class, sessionId, subjectId);
        if (visitExists == null || visitExists == 0) throw new IllegalArgumentException("访视与受试者不匹配");
        if (fileSize <= 0 || totalChunks <= 0 || totalChunks > 10000) {
            throw new IllegalArgumentException("文件大小或分片数量无效");
        }
        String safeFileName = safeName(fileName);
        String lower = safeFileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".rar")) {
            throw new IllegalArgumentException("综合导入目前支持 ZIP 和 RAR 压缩包");
        }
        String uploadId = UUID.randomUUID().toString();
        Path uploadDir = storageRoot.resolve("chunks").resolve(uploadId).normalize();
        ensureUnderStorage(uploadDir);
        Files.createDirectories(uploadDir);
        Properties metadata = new Properties();
        metadata.setProperty("subjectId", String.valueOf(subjectId));
        metadata.setProperty("sessionId", String.valueOf(sessionId));
        metadata.setProperty("fileName", safeFileName);
        metadata.setProperty("fileSize", String.valueOf(fileSize));
        metadata.setProperty("totalChunks", String.valueOf(totalChunks));
        metadata.setProperty("userId", String.valueOf(userId == null ? 0 : userId));
        try (OutputStream out = Files.newOutputStream(uploadDir.resolve("upload.properties"))) {
            metadata.store(out, "unified import chunk upload");
        }
        return chunkStatus(uploadId, subjectId, sessionId);
    }

    public Map<String, Object> saveChunk(String uploadId, Long subjectId, Long sessionId,
                                         int chunkIndex, MultipartFile chunk) throws IOException {
        Properties metadata = chunkMetadata(uploadId, subjectId, sessionId);
        int totalChunks = Integer.parseInt(metadata.getProperty("totalChunks"));
        if (chunkIndex < 0 || chunkIndex >= totalChunks || chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("分片无效");
        }
        Path uploadDir = chunkDirectory(uploadId);
        Path target = uploadDir.resolve(String.format(Locale.ROOT, "%08d.part", chunkIndex)).normalize();
        ensureUnderStorage(target);
        Path temporary = Files.createTempFile(uploadDir, "chunk-", ".tmp");
        chunk.transferTo(temporary);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return chunkStatus(uploadId, subjectId, sessionId);
    }

    public Map<String, Object> chunkStatus(String uploadId, Long subjectId, Long sessionId)
            throws IOException {
        Properties metadata = chunkMetadata(uploadId, subjectId, sessionId);
        int totalChunks = Integer.parseInt(metadata.getProperty("totalChunks"));
        List<Integer> uploaded = new ArrayList<>();
        Path uploadDir = chunkDirectory(uploadId);
        for (int i = 0; i < totalChunks; i++) {
            if (Files.isRegularFile(uploadDir.resolve(String.format(Locale.ROOT, "%08d.part", i)))) {
                uploaded.add(i);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("fileName", metadata.getProperty("fileName"));
        result.put("fileSize", Long.parseLong(metadata.getProperty("fileSize")));
        result.put("totalChunks", totalChunks);
        result.put("uploadedChunks", uploaded);
        result.put("completed", uploaded.size() == totalChunks);
        return result;
    }

    public Map<String, Object> completeChunkUpload(String uploadId, Long subjectId, Long sessionId,
                                                   Long userId) throws Exception {
        Properties metadata = chunkMetadata(uploadId, subjectId, sessionId);
        Map<String, Object> status = chunkStatus(uploadId, subjectId, sessionId);
        if (!Boolean.TRUE.equals(status.get("completed"))) {
            throw new IllegalArgumentException("仍有分片未上传完成");
        }
        Path uploadDir = chunkDirectory(uploadId);
        String original = metadata.getProperty("fileName");
        Path assembled = Files.createTempFile(storageRoot, "visit-package-",
            original.toLowerCase(Locale.ROOT).endsWith(".rar") ? ".rar" : ".zip");
        int totalChunks = Integer.parseInt(metadata.getProperty("totalChunks"));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assembled))) {
            for (int i = 0; i < totalChunks; i++) {
                Files.copy(uploadDir.resolve(String.format(Locale.ROOT, "%08d.part", i)), out);
            }
        }
        long expectedSize = Long.parseLong(metadata.getProperty("fileSize"));
        if (Files.size(assembled) != expectedSize) {
            Files.deleteIfExists(assembled);
            throw new IllegalArgumentException("分片合并后的文件大小不一致，请重传缺失分片");
        }
        try {
            return analyzeStaged(assembled, original, subjectId, sessionId, userId);
        } finally {
            deleteDirectory(uploadDir);
        }
    }

    private Properties chunkMetadata(String uploadId, Long subjectId, Long sessionId) throws IOException {
        Path metadataPath = chunkDirectory(uploadId).resolve("upload.properties");
        if (!Files.isRegularFile(metadataPath)) throw new IllegalArgumentException("上传任务不存在或已过期");
        Properties metadata = new Properties();
        try (InputStream in = Files.newInputStream(metadataPath)) { metadata.load(in); }
        if (!String.valueOf(subjectId).equals(metadata.getProperty("subjectId"))
                || !String.valueOf(sessionId).equals(metadata.getProperty("sessionId"))) {
            throw new IllegalArgumentException("上传任务不属于当前受试者或访视");
        }
        return metadata;
    }

    private Path chunkDirectory(String uploadId) {
        if (uploadId == null || !uploadId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("上传任务编号无效");
        }
        Path path = storageRoot.resolve("chunks").resolve(uploadId).normalize();
        ensureUnderStorage(path);
        return path;
    }

    private void ensureUnderStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new IllegalArgumentException("非法存储路径");
        }
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    public Map<String, Object> detail(Long batchId) {
        Map<String, Object> batch = new LinkedHashMap<>(jdbc.queryForMap(
            "SELECT id,subject_id AS subjectId,session_id AS sessionId,original_file_name AS originalFileName," +
            "file_sha256 AS fileSha256,status,total_files AS totalFiles,included_files AS includedFiles," +
            "error_message AS errorMessage,created_at AS createdAt,updated_at AS updatedAt " +
            "FROM unified_import_batch WHERE id=?", batchId));
        int previewLimit = 500;
        List<Map<String, Object>> previewItems = jdbc.queryForList(
            "SELECT id,relative_path AS relativePath,file_name AS fileName,file_size AS fileSize," +
            "detected_module AS detectedModule,COALESCE(confirmed_module,detected_module) AS confirmedModule," +
            "confidence,included,status,error_message AS errorMessage FROM unified_import_item " +
            "WHERE batch_id=? ORDER BY detected_module,relative_path LIMIT ?", batchId, previewLimit);
        batch.put("items", previewItems);
        Integer itemCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM unified_import_item WHERE batch_id=?", Integer.class, batchId);
        batch.put("previewItemCount", previewItems.size());
        batch.put("itemsTruncated", itemCount != null && itemCount > previewItems.size());
        batch.put("jobs", jdbc.queryForList(
            "SELECT id,module,status,item_count AS itemCount,result_json AS resultJson,error_message AS errorMessage " +
            "FROM unified_import_job WHERE batch_id=? ORDER BY module", batchId));
        return batch;
    }

    public List<Map<String, Object>> list(Long sessionId) {
        return jdbc.queryForList(
            "SELECT id,original_file_name AS originalFileName,status,total_files AS totalFiles," +
            "included_files AS includedFiles,created_at AS createdAt FROM unified_import_batch " +
            "WHERE session_id=? ORDER BY id DESC", sessionId);
    }

    public void assertBatchSession(Long batchId, Long sessionId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM unified_import_batch WHERE id=? AND session_id=?",
            Integer.class, batchId, sessionId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("导入批次不存在或不属于当前访视");
        }
    }

    @Transactional
    public Map<String, Object> confirm(Long batchId, List<Map<String, Object>> changes, Long userId,
                                      String authorization) {
        String currentStatus = jdbc.queryForObject(
            "SELECT status FROM unified_import_batch WHERE id=?", String.class, batchId);
        if ("COMPLETED".equals(currentStatus)) return detail(batchId);
        if (!"AWAITING_CONFIRMATION".equals(currentStatus)) {
            throw new IllegalStateException("只有等待确认的导入批次可以确认；失败任务请使用重试");
        }
        for (Map<String, Object> change : changes == null ? List.<Map<String, Object>>of() : changes) {
            Long id = ((Number) change.get("id")).longValue();
            String module = String.valueOf(change.getOrDefault("confirmedModule", "OTHER")).toUpperCase(Locale.ROOT);
            if (!Set.of("IMAGING", "GENETICS", "LAB", "ATTACHMENT", "OTHER").contains(module)) {
                throw new IllegalArgumentException("文件分类无效");
            }
            boolean included = !Boolean.FALSE.equals(change.get("included"));
            jdbc.update("UPDATE unified_import_item SET confirmed_module=?,included=? WHERE id=? AND batch_id=?",
                module, included, id, batchId);
        }
        jdbc.update("DELETE FROM unified_import_job WHERE batch_id=?", batchId);
        List<Map<String, Object>> counts = jdbc.queryForList(
            "SELECT COALESCE(confirmed_module,detected_module) module,COUNT(*) itemCount " +
            "FROM unified_import_item WHERE batch_id=? AND included=1 GROUP BY module", batchId);
        for (Map<String, Object> count : counts) {
            jdbc.update("INSERT INTO unified_import_job(batch_id,module,status,item_count,result_json) " +
                    "VALUES (?,?,'PROCESSING',?,JSON_OBJECT('message','正在写入专业模块'))",
                batchId, count.get("module"), count.get("itemCount"));
        }
        jdbc.update("UPDATE unified_import_batch SET status='IMPORTING',confirmed_by=?," +
            "included_files=(SELECT COUNT(*) FROM unified_import_item WHERE batch_id=? AND included=1) WHERE id=?",
            userId, batchId, batchId);
        jdbc.update("""
            INSERT INTO audit_log(user_id,created_at,updated_at,operation_type,operation_detail,
                                  target_id,target_type,operation_result)
            VALUES (?,NOW(6),NOW(6),'UNIFIED_IMPORT_CONFIRM','确认综合资料分类',
                    ?,'UNIFIED_IMPORT_BATCH','SUCCESS')
            """, userId, batchId);
        String jobAuthorization = createJobAuthorization(authorization);
        CompletableFuture.runAsync(() -> ingestModules(batchId, jobAuthorization, false));
        return detail(batchId);
    }

    @Transactional
    public Map<String, Object> retry(Long batchId, String authorization) {
        String currentStatus = jdbc.queryForObject(
            "SELECT status FROM unified_import_batch WHERE id=?", String.class, batchId);
        if (!Set.of("FAILED", "PARTIAL_FAILED").contains(currentStatus)) {
            throw new IllegalStateException("只有失败或部分失败的导入批次可以重试");
        }
        Integer failed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM unified_import_item WHERE batch_id=? AND included=1 AND status='FAILED'",
            Integer.class, batchId);
        if (failed == null || failed == 0) {
            throw new IllegalStateException("当前批次没有可重试的失败文件");
        }
        jdbc.update("UPDATE unified_import_job SET status='PROCESSING',error_message=NULL," +
            "retry_count=retry_count+1 WHERE batch_id=? AND status IN ('FAILED','PARTIAL_FAILED')", batchId);
        jdbc.update("UPDATE unified_import_batch SET status='IMPORTING',error_message=NULL WHERE id=?", batchId);
        String jobAuthorization = createJobAuthorization(authorization);
        CompletableFuture.runAsync(() -> ingestModules(batchId, jobAuthorization, true));
        return detail(batchId);
    }

    private String createJobAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("缺少有效的登录凭证");
        }
        Claims claims = JwtUtil.parseToken(authorization.substring(7));
        if (claims == null) throw new IllegalArgumentException("登录凭证已失效");
        Map<String, Object> preservedClaims = new LinkedHashMap<>();
        for (String key : List.of("permissions", "roles", "projectIds", "institutionId", "subjectId")) {
            Object value = claims.get(key);
            if (value != null) preservedClaims.put(key, value);
        }
        Long userId = Long.valueOf(claims.getId());
        return "Bearer " + JwtUtil.generateServiceJobToken(
            userId, claims.getSubject(), preservedClaims);
    }

    private void ingestModules(Long batchId, String authorization, boolean retryOnly) {
        Map<String, Object> batch = jdbc.queryForMap(
            "SELECT subject_id,session_id,storage_path FROM unified_import_batch WHERE id=?", batchId);
        Long subjectId = ((Number) batch.get("subject_id")).longValue();
        Long sessionId = ((Number) batch.get("session_id")).longValue();
        String visitCode = jdbc.queryForObject(
            "SELECT visit_label FROM session WHERE id=?", String.class, sessionId);
        Path archive = Paths.get(String.valueOf(batch.get("storage_path"))).toAbsolutePath().normalize();
        Path extraction = storageRoot.resolve("batch-" + batchId).normalize();
        if (!extraction.startsWith(storageRoot)) throw new IllegalArgumentException("导入目录无效");
        try {
            Files.createDirectories(extraction);
            extractArchive(archive, extraction);
            List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id,relative_path,COALESCE(confirmed_module,detected_module) module " +
                "FROM unified_import_item WHERE batch_id=? AND included=1" +
                    (retryOnly ? " AND status='FAILED'" : ""), batchId);
            Map<String, Integer> succeeded = new LinkedHashMap<>();
            Map<String, List<String>> failures = new LinkedHashMap<>();
            Set<String> attemptedModules = new LinkedHashSet<>();
            List<Map<String, Object>> imagingItems = items.stream()
                .filter(item -> "IMAGING".equals(String.valueOf(item.get("module"))))
                .toList();
            if (!imagingItems.isEmpty()) {
                attemptedModules.add("IMAGING");
                for (int start = 0; start < imagingItems.size(); start += 100) {
                    List<Map<String, Object>> group =
                        imagingItems.subList(start, Math.min(start + 100, imagingItems.size()));
                    List<Path> files = new ArrayList<>();
                    List<Map<String, Object>> validItems = new ArrayList<>();
                    for (Map<String, Object> item : group) {
                        Path file = extraction.resolve(String.valueOf(item.get("relative_path"))).normalize();
                        if (!file.startsWith(extraction) || !Files.isRegularFile(file)) {
                            recordFailure(((Number) item.get("id")).longValue(), "IMAGING",
                                "解压后的文件不存在", failures);
                        } else {
                            files.add(file);
                            validItems.add(item);
                        }
                    }
                    if (files.isEmpty()) continue;
                    try {
                        ingestImagingFiles(files, sessionId, authorization);
                        for (Map<String, Object> item : validItems) {
                            jdbc.update("UPDATE unified_import_item SET status='COMPLETED',error_message=NULL WHERE id=?",
                                item.get("id"));
                        }
                        succeeded.merge("IMAGING", validItems.size(), Integer::sum);
                    } catch (Exception ex) {
                        String error = truncate(ex.getMessage());
                        for (Map<String, Object> item : validItems) {
                            recordFailure(((Number) item.get("id")).longValue(), "IMAGING",
                                error, failures);
                        }
                    }
                }
            }
            for (Map<String, Object> item : items) {
                Long itemId = ((Number) item.get("id")).longValue();
                String relative = String.valueOf(item.get("relative_path"));
                String module = String.valueOf(item.get("module"));
                if ("IMAGING".equals(module)) continue;
                attemptedModules.add(module);
                Path file = extraction.resolve(relative).normalize();
                if (!file.startsWith(extraction) || !Files.isRegularFile(file)) {
                    recordFailure(itemId, module, "解压后的文件不存在", failures);
                    continue;
                }
                try {
                    ingestFile(module, file, subjectId, sessionId, visitCode, authorization);
                    jdbc.update("UPDATE unified_import_item SET status='COMPLETED',error_message=NULL WHERE id=?", itemId);
                    succeeded.merge(module, 1, Integer::sum);
                } catch (Exception e) {
                    recordFailure(itemId, module, truncate(e.getMessage()), failures);
                }
            }
            for (Map<String, Object> job : jdbc.queryForList(
                    "SELECT id,module FROM unified_import_job WHERE batch_id=?", batchId)) {
                String module = String.valueOf(job.get("module"));
                if (!attemptedModules.contains(module)) continue;
                List<String> moduleFailures = failures.getOrDefault(module, List.of());
                String status = moduleFailures.isEmpty() ? "COMPLETED"
                    : succeeded.getOrDefault(module, 0) > 0 ? "PARTIAL_FAILED" : "FAILED";
                Integer totalCompleted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM unified_import_item WHERE batch_id=? AND included=1 " +
                        "AND COALESCE(confirmed_module,detected_module)=? AND status='COMPLETED'",
                    Integer.class, batchId, module);
                String result;
                try {
                    result = objectMapper.writeValueAsString(Map.of(
                        "imported", totalCompleted == null ? 0 : totalCompleted,
                        "failed", moduleFailures.size()));
                } catch (Exception ignored) { result = "{}"; }
                jdbc.update("UPDATE unified_import_job SET status=?,result_json=?,error_message=? WHERE id=?",
                    status, result, moduleFailures.isEmpty() ? null : String.join("；", moduleFailures),
                    job.get("id"));
            }
            Integer remainingFailures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM unified_import_item WHERE batch_id=? AND included=1 AND status='FAILED'",
                Integer.class, batchId);
            Integer completedItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM unified_import_item WHERE batch_id=? AND included=1 AND status='COMPLETED'",
                Integer.class, batchId);
            String batchStatus = remainingFailures == null || remainingFailures == 0 ? "COMPLETED"
                : completedItems != null && completedItems > 0 ? "PARTIAL_FAILED" : "FAILED";
            jdbc.update("UPDATE unified_import_batch SET status=?,error_message=? WHERE id=?",
                batchStatus, remainingFailures == null || remainingFailures == 0
                    ? null : "部分文件导入失败，请查看模块任务或重试", batchId);
        } catch (Exception e) {
            jdbc.update("UPDATE unified_import_batch SET status='FAILED',error_message=? WHERE id=?",
                truncate(e.getMessage()), batchId);
        } finally {
            deleteTree(extraction);
        }
    }

    private void ingestFile(String module, Path file, Long subjectId, Long sessionId,
                            String visitCode, String authorization) throws Exception {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", new FileSystemResource(file));
        switch (module) {
            case "GENETICS" -> {
                multipart.add("subjectId", subjectId.toString());
                multipart.add("sessionId", sessionId.toString());
                String response = postMultipart("/api/v1/genetics/samples/upload", multipart, authorization);
                JsonNode root = objectMapper.readTree(response);
                long sampleId = root.path("data").path("id").asLong();
                if (sampleId > 0) postJson("/api/v1/genetics/samples/" + sampleId + "/parse",
                    "{}", authorization);
            }
            case "LAB" -> {
                multipart.add("subjectId", subjectId.toString());
                multipart.add("sessionId", sessionId.toString());
                String response = postMultipart("/api/v1/lab/report-uploads", multipart, authorization);
                JsonNode root = objectMapper.readTree(response);
                String uploadId = root.path("data").path("id").asText();
                if (!uploadId.isBlank()) {
                    String preview = postJson("/api/v1/lab/report-uploads/" + uploadId + "/preview",
                        "{}", authorization);
                    JsonNode candidates = objectMapper.readTree(preview).path("data").path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        postJson("/api/v1/lab/report-uploads/" + uploadId + "/confirm",
                            objectMapper.writeValueAsString(candidates), authorization);
                    }
                }
            }
            case "IMAGING" -> {
                multipart.set("files", new FileSystemResource(file));
                multipart.add("sessionId", sessionId.toString());
                String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                multipart.add("modalityId", lower.contains("ct") ? "5" : "1");
                postMultipart("/api/v1/imaging/upload", multipart, authorization);
            }
            case "ATTACHMENT", "OTHER" -> {
                multipart.add("subjectId", subjectId.toString());
                multipart.add("visitCode", visitCode == null ? "UNKNOWN" : visitCode);
                multipart.add("fieldCode", "UNIFIED_IMPORT");
                postMultipart("/api/v1/scales/attachments", multipart, authorization);
            }
            default -> throw new IllegalArgumentException("未知模块：" + module);
        }
    }

    private void ingestImagingFiles(List<Path> files, Long sessionId, String authorization) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        for (Path file : files) multipart.add("files", new FileSystemResource(file));
        multipart.add("sessionId", sessionId.toString());
        multipart.add("modalityId", "1");
        postMultipart("/api/v1/imaging/upload", multipart, authorization);
    }

    private String postMultipart(String uri, MultiValueMap<String, Object> body, String authorization) {
        return restClient.post().uri(uri).header("Authorization", authorization == null ? "" : authorization)
            .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(String.class);
    }

    private String postJson(String uri, String body, String authorization) {
        return restClient.post().uri(uri).header("Authorization", authorization == null ? "" : authorization)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
    }

    private void recordFailure(Long itemId, String module, String error,
                               Map<String, List<String>> failures) {
        jdbc.update("UPDATE unified_import_item SET status='FAILED',error_message=? WHERE id=?",
            error, itemId);
        failures.computeIfAbsent(module, ignored -> new ArrayList<>()).add(error);
    }

    private void extractArchive(Path archive, Path destination) throws Exception {
        String lower = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        long[] extracted = {0L};
        int[] files = {0};
        if (lower.endsWith(".rar")) {
            if (isRar5(archive)) {
                extractRar5(archive, destination);
                validateExtractedTree(destination);
                return;
            }
            try (Archive rar = new Archive(archive.toFile())) {
                FileHeader header;
                while ((header = rar.nextFileHeader()) != null) {
                    if (header.isDirectory()) continue;
                    if (header.isEncrypted()) throw new IllegalArgumentException("不支持加密压缩包，请先解密后上传");
                    validateLimits(++files[0], extracted[0]);
                    Path target = safeTarget(destination, header.getFileNameString());
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = new LimitedOutputStream(
                            Files.newOutputStream(target), extracted)) {
                        rar.extractFile(header, out);
                    }
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    validateLimits(++files[0], extracted[0]);
                    Path target = safeTarget(destination, entry.getName());
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = new LimitedOutputStream(
                             Files.newOutputStream(target), extracted)) {
                        in.transferTo(out);
                    }
                }
            }
        }
    }

    private static Path safeTarget(Path root, String relative) {
        Path target = root.resolve(relative.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("压缩包包含危险路径");
        return target;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private List<Item> inspectZip(Path path) throws Exception {
        List<Item> items = new ArrayList<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                validatePath(entry.getName());
                total += Math.max(0, entry.getSize());
                validateLimits(items.size() + 1, total);
                byte[] prefix;
                try (InputStream in = zip.getInputStream(entry)) { prefix = in.readNBytes(512); }
                items.add(item(entry.getName(), entry.getSize(), prefix));
            }
        }
        return items;
    }

    private List<Item> inspectRar(Path path) throws Exception {
        if (isRar5(path)) return inspectRar5(path);
        List<Item> items = new ArrayList<>();
        long total = 0;
        try (Archive archive = new Archive(path.toFile())) {
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (header.isDirectory()) continue;
                if (header.isEncrypted()) throw new IllegalArgumentException("不支持加密压缩包，请先解密后上传");
                String name = header.getFileNameString();
                validatePath(name);
                total += header.getFullUnpackSize();
                validateLimits(items.size() + 1, total);
                PrefixOutputStream out = new PrefixOutputStream(512);
                archive.extractFile(header, out);
                items.add(item(name, header.getFullUnpackSize(), out.bytes()));
            }
        }
        return items;
    }

    private List<Item> inspectRar5(Path archive) throws Exception {
        Path extraction = Files.createTempDirectory(storageRoot, "rar5-inspect-");
        try {
            extractRar5(archive, extraction);
            validateExtractedTree(extraction);
            List<Item> items = new ArrayList<>();
            long total = 0;
            try (var paths = Files.walk(extraction)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String relative = extraction.relativize(file).toString().replace('\\', '/');
                    long size = Files.size(file);
                    total += size;
                    validateLimits(items.size() + 1, total);
                    byte[] prefix;
                    try (InputStream in = Files.newInputStream(file)) { prefix = in.readNBytes(512); }
                    items.add(item(relative, size, prefix));
                }
            }
            return items;
        } finally {
            deleteTree(extraction);
        }
    }

    private void extractRar5(Path archive, Path destination) throws Exception {
        List<String> entries = runArchiveCommand(List.of(
            "tar", "-tf", archive.toAbsolutePath().toString()));
        int count = 0;
        for (String entry : entries) {
            if (entry.isBlank() || entry.endsWith("/") || entry.endsWith("\\")) continue;
            validatePath(entry);
            validateLimits(++count, 0);
        }
        Files.createDirectories(destination);
        runArchiveCommand(List.of(
            "tar", "-xf", archive.toAbsolutePath().toString(),
            "-C", destination.toAbsolutePath().toString()));
    }

    private List<String> runArchiveCommand(List<String> command) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                "服务器缺少 RAR5 解压组件（libarchive/bsdtar），请联系管理员安装", ex);
        }
        List<String> output;
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            output = reader.lines().toList();
        }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalArgumentException("RAR5 解压超时");
        }
        if (process.exitValue() != 0) {
            String detail = String.join("；", output);
            throw new IllegalArgumentException("RAR5 压缩包无法解压" +
                (detail.isBlank() ? "" : "：" + truncate(detail)));
        }
        return output;
    }

    private void validateExtractedTree(Path root) throws IOException {
        long total = 0;
        int count = 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("压缩包包含不安全的符号链接");
                }
                if (Files.isRegularFile(path)) {
                    total += Files.size(path);
                    validateLimits(++count, total);
                }
            }
        }
    }

    private boolean isRar5(Path path) throws IOException {
        byte[] signature;
        try (InputStream in = Files.newInputStream(path)) { signature = in.readNBytes(8); }
        byte[] rar5 = {'R', 'a', 'r', '!', 0x1a, 0x07, 0x01, 0x00};
        return Arrays.equals(signature, rar5);
    }

    private static Item item(String path, long size, byte[] prefix) {
        String name = Paths.get(path.replace('\\', '/')).getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String module;
        double confidence;
        boolean dicomMagic = prefix.length >= 132 && prefix[128] == 'D' && prefix[129] == 'I'
            && prefix[130] == 'C' && prefix[131] == 'M';
        if (dicomMagic || lower.endsWith(".dcm") || lower.endsWith(".nii") || lower.endsWith(".nii.gz")) {
            module = "IMAGING"; confidence = dicomMagic ? 0.99 : 0.9;
        } else if (lower.matches(".*\\.(vcf|vcf\\.gz|fastq|fastq\\.gz|fq|fq\\.gz|bam|cram|bed|bim|fam)$")) {
            module = "GENETICS"; confidence = 0.95;
        } else if (lower.matches(".*\\.(csv|xlsx|xls|pdf|png|jpg|jpeg)$")
                && (lower.contains("lab") || lower.contains("检验") || lower.contains("化验"))) {
            module = "LAB"; confidence = 0.85;
        } else if (lower.matches(".*\\.(csv|xlsx|xls|pdf|png|jpg|jpeg)$")) {
            module = "ATTACHMENT"; confidence = 0.55;
        } else {
            module = "OTHER"; confidence = 0.3;
        }
        return new Item(path.replace('\\', '/'), name, Math.max(0, size), module, confidence);
    }

    private static void validatePath(String name) {
        Path normalized = Paths.get(name.replace('\\', '/')).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("压缩包包含危险路径：" + name);
        }
    }

    private static void validateLimits(int count, long size) {
        if (count > MAX_FILES) throw new IllegalArgumentException("压缩包文件数超过 20000");
        if (size > MAX_UNCOMPRESSED) throw new IllegalArgumentException("压缩包解压后大小超过 50GB");
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "visit-package.zip";
        return Paths.get(value).getFileName().toString();
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String truncate(String value) {
        if (value == null) return "导入失败";
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private record Item(String path, String name, long size, String module, double confidence) {}

    private static final class LimitedOutputStream extends FilterOutputStream {
        private final long[] total;

        private LimitedOutputStream(OutputStream out, long[] total) {
            super(out);
            this.total = total;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            out.write(value);
            total[0]++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            out.write(bytes, offset, length);
            total[0] += length;
        }

        private void ensureCapacity(int length) {
            if (total[0] + length > MAX_UNCOMPRESSED) {
                throw new IllegalArgumentException("压缩包实际解压大小超过 50GB");
            }
        }
    }

    private static final class PrefixOutputStream extends OutputStream {
        private final ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        private final int limit;
        private PrefixOutputStream(int limit) { this.limit = limit; }
        @Override public void write(int value) {
            if (prefix.size() < limit) prefix.write(value);
        }
        @Override public void write(byte[] bytes, int offset, int length) {
            int remaining = limit - prefix.size();
            if (remaining > 0) prefix.write(bytes, offset, Math.min(remaining, length));
        }
        private byte[] bytes() { return prefix.toByteArray(); }
    }
}
