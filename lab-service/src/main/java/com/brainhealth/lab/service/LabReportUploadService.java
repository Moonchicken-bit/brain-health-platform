package com.brainhealth.lab.service;

import com.brainhealth.lab.entity.LabReportUpload;
import com.brainhealth.lab.repository.LabReportUploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class LabReportUploadService {
    private static final Set<String> ALLOWED = Set.of(
        "pdf", "png", "jpg", "jpeg", "tif", "tiff", "zip", "rar", "xls", "xlsx", "csv");
    private final LabReportUploadRepository repository;
    private final Path storageRoot;

    public LabReportUploadService(
            LabReportUploadRepository repository,
            @Value("${brain-health.lab-report.storage-root:${java.io.tmpdir}/brain-health/lab-reports}") String storageRoot) {
        this.repository = repository;
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public LabReportUpload store(MultipartFile file, Long subjectId, Long sessionId) {
        if (subjectId == null || subjectId < 1 || sessionId == null || sessionId < 1) {
            throw new IllegalArgumentException("受试者和访视不能为空");
        }
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件为空");
        String originalName = safeFileName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (!ALLOWED.contains(extension)) throw new IllegalArgumentException("不支持的检验报告格式：" + extension);
        String id = UUID.randomUUID().toString();
        Path target = storageRoot.resolve(id + "." + extension).normalize();
        if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("无效的存储路径");
        try {
            Files.createDirectories(storageRoot);
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LabReportUpload upload = new LabReportUpload();
            upload.setId(id);
            upload.setSubjectId(subjectId);
            upload.setSessionId(sessionId);
            upload.setOriginalName(originalName);
            upload.setStoragePath(target.toString());
            upload.setContentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));
            upload.setFileSize(Files.size(target));
            upload.setStatus("UPLOADED");
            return repository.save(upload);
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new IllegalArgumentException("检验报告保存失败：" + e.getMessage(), e);
        }
    }

    public List<LabReportUpload> list(Long subjectId, Long sessionId) {
        return repository.findBySubjectIdAndSessionIdOrderByCreatedAtDesc(subjectId, sessionId);
    }

    public LabReportUpload get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("检验报告不存在"));
    }

    public Resource resource(String id) {
        try {
            Path path = Paths.get(get(id).getStoragePath()).toAbsolutePath().normalize();
            if (!path.startsWith(storageRoot)) throw new IllegalArgumentException("无效的文件路径");
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) throw new IllegalArgumentException("检验报告文件不存在");
            return resource;
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取检验报告", e);
        }
    }

    public void delete(String id) {
        LabReportUpload upload = get(id);
        try { Files.deleteIfExists(Paths.get(upload.getStoragePath())); }
        catch (IOException e) { throw new IllegalArgumentException("检验报告文件删除失败", e); }
        repository.delete(upload);
    }

    private static String safeFileName(String original) {
        String name = original == null ? "report.bin" : Paths.get(original).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return name.isBlank() ? "report.bin" : name.substring(0, Math.min(name.length(), 240));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
