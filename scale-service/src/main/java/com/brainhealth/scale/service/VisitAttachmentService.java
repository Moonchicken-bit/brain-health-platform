package com.brainhealth.scale.service;

import com.brainhealth.scale.dto.VisitAttachmentDTO;
import com.brainhealth.scale.entity.VisitAttachment;
import com.brainhealth.scale.repository.VisitAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VisitAttachmentService {
    private static final long DEFAULT_MAX_SIZE = 50L * 1024L * 1024L;

    private final VisitAttachmentRepository repository;
    private final Path storageRoot;
    private final long maxSize;

    public VisitAttachmentService(
            VisitAttachmentRepository repository,
            @Value("${visit.attachment.storage-root:./data/visit-attachments}") String storageRoot,
            @Value("${visit.attachment.max-size-bytes:52428800}") long maxSize) {
        this.repository = repository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
    }

    @Transactional
    public VisitAttachmentDTO store(
            MultipartFile file,
            Long subjectId,
            String visitCode,
            String fieldCode) {
        validate(file, subjectId, visitCode, fieldCode);
        String id = UUID.randomUUID().toString();
        String originalName = safeDisplayName(file.getOriginalFilename());
        Path target = storageRoot.resolve(id.substring(0, 2)).resolve(id).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("附件存储路径无效");
        }

        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), id, ".upload");
            try (var input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Preserve the original storage failure.
            }
            throw new IllegalStateException("附件保存失败，请重试", atomicMoveFailure);
        }

        VisitAttachment attachment = new VisitAttachment();
        attachment.setId(id);
        attachment.setSubjectId(subjectId);
        attachment.setVisitCode(visitCode.trim());
        attachment.setFieldCode(fieldCode.trim());
        attachment.setOriginalName(originalName);
        attachment.setObjectKey(storageRoot.relativize(target).toString());
        attachment.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        attachment.setSize(file.getSize());
        attachment.setCreatedAt(LocalDateTime.now());
        try {
            return VisitAttachmentDTO.from(repository.save(attachment));
        } catch (RuntimeException persistenceFailure) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Preserve the persistence failure.
            }
            throw persistenceFailure;
        }
    }

    @Transactional(readOnly = true)
    public VisitAttachment get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("附件不存在"));
    }

    @Transactional(readOnly = true)
    public Resource loadContent(String id) {
        VisitAttachment attachment = get(id);
        Path target = storageRoot.resolve(attachment.getObjectKey()).normalize();
        if (!target.startsWith(storageRoot) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("附件文件不存在");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败", e);
        }
    }

    @Transactional
    public void delete(String id) {
        VisitAttachment attachment = get(id);
        Path target = storageRoot.resolve(attachment.getObjectKey()).normalize();
        if (target.startsWith(storageRoot)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                throw new IllegalStateException("附件删除失败，请重试", e);
            }
        }
        repository.delete(attachment);
    }

    private void validate(MultipartFile file, Long subjectId, String visitCode, String fieldCode) {
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("请选择受试者");
        }
        if (visitCode == null || visitCode.isBlank() || visitCode.length() > 32) {
            throw new IllegalArgumentException("访视代码无效");
        }
        if (fieldCode == null || fieldCode.isBlank() || fieldCode.length() > 160) {
            throw new IllegalArgumentException("附件字段代码无效");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空文件");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("附件超过 50 MB 限制");
        }
    }

    private String safeDisplayName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        String fileName = Path.of(originalName).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (fileName.isBlank()) {
            return "attachment";
        }
        return fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName;
    }
}
