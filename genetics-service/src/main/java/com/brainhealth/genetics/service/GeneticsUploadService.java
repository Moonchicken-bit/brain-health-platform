package com.brainhealth.genetics.service;

import com.brainhealth.genetics.entity.GeneticsSample;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GeneticsUploadService {
    private static final Pattern SAFE_UPLOAD_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{7,79}$");

    private final GeneticsService geneticsService;
    private final Path storageRoot;
    private final Path chunkRoot;
    private final long maxFileSize;

    public GeneticsUploadService(
            GeneticsService geneticsService,
            @Value("${genetics.upload.storage-root:./data/genetics}") String storageRoot,
            @Value("${genetics.upload.chunk-root:./data/genetics-chunks}") String chunkRoot,
            @Value("${genetics.upload.max-file-size-bytes:5368709120}") long maxFileSize) {
        this.geneticsService = geneticsService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.chunkRoot = Path.of(chunkRoot).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public GeneticsSample storeDirect(
            MultipartFile file,
            Long subjectId,
            Long sessionId,
            String platform,
            String referenceGenome,
            String sampleType) {
        validateContext(subjectId, file.getOriginalFilename(), file.getSize());
        String uploadId = UUID.randomUUID().toString();
        Path target = storageTarget(uploadId, file.getOriginalFilename());
        try {
            Files.createDirectories(target.getParent());
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("遗传文件保存失败，请重试", e);
        }
        return createSample(subjectId, sessionId, platform, referenceGenome, sampleType, file.getOriginalFilename(), target);
    }

    public void storeChunk(
            MultipartFile chunk,
            String uploadId,
            int chunkIndex,
            int totalChunks,
            long declaredFileSize,
            String fileName) {
        validateUploadId(uploadId);
        validateFileName(fileName);
        if (chunkIndex < 0 || totalChunks <= 0 || chunkIndex >= totalChunks || totalChunks > 10000) {
            throw new IllegalArgumentException("分片序号或总数无效");
        }
        if (declaredFileSize <= 0 || declaredFileSize > maxFileSize) {
            throw new IllegalArgumentException("遗传文件大小超出限制");
        }
        if (chunk.isEmpty()) {
            throw new IllegalArgumentException("上传分片为空");
        }
        Path directory = chunkDirectory(uploadId);
        Path target = directory.resolve(String.format(Locale.ROOT, "chunk_%05d", chunkIndex)).normalize();
        if (!target.startsWith(directory)) {
            throw new IllegalArgumentException("分片路径无效");
        }
        try {
            Files.createDirectories(directory);
            try (var input = chunk.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("分片保存失败，请重试", e);
        }
    }

    public GeneticsSample merge(
            String uploadId,
            String fileName,
            int totalChunks,
            long declaredFileSize,
            Long subjectId,
            Long sessionId,
            String platform,
            String referenceGenome,
            String sampleType) {
        validateUploadId(uploadId);
        validateContext(subjectId, fileName, declaredFileSize);
        if (totalChunks <= 0 || totalChunks > 10000) {
            throw new IllegalArgumentException("分片总数无效");
        }
        Path directory = chunkDirectory(uploadId);
        Path target = storageTarget(uploadId, fileName);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(target)) {
                for (int index = 0; index < totalChunks; index++) {
                    Path chunk = directory.resolve(String.format(Locale.ROOT, "chunk_%05d", index)).normalize();
                    if (!chunk.startsWith(directory) || !Files.isRegularFile(chunk)) {
                        throw new IllegalArgumentException("缺少分片 " + index + "，请重新上传");
                    }
                    Files.copy(chunk, output);
                }
            }
            long actualSize = Files.size(target);
            if (actualSize != declaredFileSize) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException(
                        "合并后的文件大小不一致，期望 " + declaredFileSize + "，实际 " + actualSize);
            }
            GeneticsSample sample =
                    createSample(subjectId, sessionId, platform, referenceGenome, sampleType, fileName, target);
            deleteRecursively(directory);
            return sample;
        } catch (IOException e) {
            throw new IllegalStateException("遗传文件合并失败，请重试", e);
        }
    }

    private GeneticsSample createSample(
            Long subjectId,
            Long sessionId,
            String platform,
            String referenceGenome,
            String sampleType,
            String originalName,
            Path target) {
        GeneticsSample sample = new GeneticsSample();
        sample.setSubjectId(subjectId);
        sample.setPlatform(blankToDefault(platform, "Unknown"));
        sample.setReferenceGenome(blankToDefault(referenceGenome, "hg38"));
        sample.setSampleType(blankToDefault(sampleType, "Blood"));
        sample.setVcfFileName(safeDisplayName(originalName));
        sample.setVcfFilePath(storageRoot.relativize(target).toString());
        sample.setQcStatus("Pending");
        if (sessionId != null) {
            sample.setNotes("sessionId=" + sessionId);
        }
        try {
            return geneticsService.createSample(sample);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Preserve the database error.
            }
            throw e;
        }
    }

    private void validateContext(Long subjectId, String fileName, long size) {
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("请选择受试者");
        }
        validateFileName(fileName);
        if (size <= 0 || size > maxFileSize) {
            throw new IllegalArgumentException("遗传文件为空或超过大小限制");
        }
    }

    private void validateFileName(String fileName) {
        String name = safeDisplayName(fileName).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".vcf") && !name.endsWith(".vcf.gz") && !name.endsWith(".vcf.bgz")) {
            throw new IllegalArgumentException("仅支持 VCF、VCF.GZ 或 VCF.BGZ 文件");
        }
    }

    private void validateUploadId(String uploadId) {
        if (uploadId == null || !SAFE_UPLOAD_ID.matcher(uploadId).matches()) {
            throw new IllegalArgumentException("上传 ID 无效");
        }
    }

    private Path chunkDirectory(String uploadId) {
        Path directory = chunkRoot.resolve(uploadId).normalize();
        if (!directory.startsWith(chunkRoot)) {
            throw new IllegalArgumentException("分片目录无效");
        }
        return directory;
    }

    private Path storageTarget(String uploadId, String originalName) {
        String lowerName = safeDisplayName(originalName).toLowerCase(Locale.ROOT);
        String suffix = lowerName.endsWith(".vcf.bgz")
                ? ".vcf.bgz"
                : lowerName.endsWith(".vcf.gz") ? ".vcf.gz" : ".vcf";
        Path target = storageRoot.resolve(uploadId.substring(0, 2)).resolve(uploadId + suffix).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("遗传文件存储路径无效");
        }
        return target;
    }

    private String safeDisplayName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String fileName = Path.of(originalName).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("文件名无效");
        }
        return fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("临时分片清理失败", e);
                }
            });
        }
    }
}
