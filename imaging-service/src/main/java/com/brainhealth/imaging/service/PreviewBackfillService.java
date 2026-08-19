package com.brainhealth.imaging.service;

import com.brainhealth.imaging.entity.ImagingSeries;
import com.brainhealth.imaging.repository.ImagingSeriesRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class PreviewBackfillService {
    private static final String BUCKET = "brain-health-imaging";
    private final MinioClient minio;
    private final ImagingSeriesRepository seriesRepository;
    private final ArchiveInspectionService archiveService;
    private final DicomMetadataService metadataService;
    private final DicomPreviewService previewService;
    private final Path workRoot;

    public PreviewBackfillService(
            MinioClient minio,
            ImagingSeriesRepository seriesRepository,
            ArchiveInspectionService archiveService,
            DicomMetadataService metadataService,
            DicomPreviewService previewService,
            @Value("${brain-health.imaging.preview-root:${java.io.tmpdir}/brain-health/preview-backfill}") String root) {
        this.minio = minio;
        this.seriesRepository = seriesRepository;
        this.archiveService = archiveService;
        this.metadataService = metadataService;
        this.previewService = previewService;
        this.workRoot = Paths.get(root).toAbsolutePath().normalize();
    }

    public synchronized Map<String, Object> regenerate(Long imagingSessionId) {
        List<ImagingSeries> series = seriesRepository.findByImagingSessionId(imagingSessionId);
        if (series.isEmpty()) throw new IllegalArgumentException("影像检查没有序列");
        String sourceObject = series.get(0).getFilePath();
        if (sourceObject == null || sourceObject.isBlank()) throw new IllegalArgumentException("影像检查没有原始文件");
        Path task = workRoot.resolve(UUID.randomUUID().toString());
        Path archive = task.resolve(fileName(sourceObject));
        Path extracted = task.resolve("files");
        int generated = 0;
        List<String> failures = new ArrayList<>();
        try {
            Files.createDirectories(task);
            try (var input = minio.getObject(GetObjectArgs.builder().bucket(BUCKET).object(sourceObject).build())) {
                Files.copy(input, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            var extraction = archiveService.extract(archive, archive.getFileName().toString(), extracted);
            Map<String, ImagingSeries> byUid = new HashMap<>();
            for (ImagingSeries item : series) {
                if (item.getSeriesUid() != null) byUid.put(item.getSeriesUid(), item);
            }
            Set<Long> completed = new HashSet<>();
            for (var file : extraction.files()) {
                if (!"DICOM".equals(file.fileType())) continue;
                Path dicom = extracted.resolve(file.relativePath()).normalize();
                try {
                    String uid = metadataService.read(dicom).seriesInstanceUid();
                    ImagingSeries target = byUid.get(uid);
                    if (target == null || completed.contains(target.getId())) continue;
                    byte[] png = previewService.createPng(dicom);
                    minio.putObject(PutObjectArgs.builder()
                        .bucket(BUCKET)
                        .object("series/" + target.getId() + "/preview.png")
                        .stream(new ByteArrayInputStream(png), png.length, -1)
                        .contentType("image/png")
                        .build());
                    completed.add(target.getId());
                    generated++;
                    if (completed.size() == series.size()) break;
                } catch (Exception error) {
                    if (failures.size() < 20) failures.add(file.relativePath() + ": " + error.getMessage());
                }
            }
            return Map.of(
                "totalSeries", series.size(),
                "generated", generated,
                "missing", series.size() - generated,
                "failures", failures);
        } catch (Exception error) {
            throw new IllegalStateException("预览生成失败: " + error.getMessage(), error);
        } finally {
            deleteTree(task);
        }
    }

    private static String fileName(String objectName) {
        String normalized = objectName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
