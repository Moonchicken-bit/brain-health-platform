package com.brainhealth.imaging.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class ArchiveAnalysisService {
    private final ArchiveInspectionService archiveService;
    private final DicomMetadataService dicomService;
    private final DicomPreviewService previewService;

    @Autowired
    public ArchiveAnalysisService(ArchiveInspectionService archiveService, DicomMetadataService dicomService,
                                  DicomPreviewService previewService) {
        this.archiveService = archiveService;
        this.dicomService = dicomService;
        this.previewService = previewService;
    }

    ArchiveAnalysisService(ArchiveInspectionService archiveService, DicomMetadataService dicomService) {
        this(archiveService, dicomService, new DicomPreviewService());
    }

    public ArchiveAnalysis analyze(Path archive, String fileName, Path extractionRoot) {
        var extraction = archiveService.extract(archive, fileName, extractionRoot);
        Map<String, MutableSeries> series = new LinkedHashMap<>();
        Set<String> patientIds = new LinkedHashSet<>();
        Set<String> patientNames = new LinkedHashSet<>();
        Set<String> studyUids = new LinkedHashSet<>();
        int dicomCount = 0;
        List<String> warnings = new ArrayList<>();

        for (var file : extraction.files()) {
            if (!"DICOM".equals(file.fileType())) continue;
            dicomCount++;
            Path path = extractionRoot.resolve(file.relativePath()).normalize();
            try {
                var metadata = dicomService.read(path);
                add(patientIds, metadata.patientId());
                add(patientNames, metadata.patientName());
                add(studyUids, metadata.studyInstanceUid());
                String key = firstNonBlank(metadata.seriesInstanceUid(), metadata.seriesDescription(),
                    path.getParent() == null ? file.relativePath() : extractionRoot.relativize(path.getParent()).toString());
                MutableSeries group = series.computeIfAbsent(key, ignored -> new MutableSeries(metadata));
                group.fileCount++;
                group.totalBytes += file.size();
                if (group.previewBase64 == null) {
                    try {
                        group.previewBase64 = Base64.getEncoder().encodeToString(previewService.createPng(path));
                    } catch (IOException ignored) {
                        // Unsupported compressed transfer syntaxes remain available for download.
                    }
                }
            } catch (IllegalArgumentException error) {
                if (warnings.size() < 20) warnings.add(file.relativePath() + "：" + error.getMessage());
            }
        }
        if (patientIds.size() > 1) warnings.add("压缩包包含多个患者编号，请核对后再导入");
        if (studyUids.size() > 1) warnings.add("压缩包包含多个检查 Study，请确认是否需要拆分导入");
        List<SeriesPreview> previews = series.values().stream().map(MutableSeries::toPreview).toList();
        return new ArchiveAnalysis(extraction.archiveType(), extraction.fileCount(), dicomCount,
            List.copyOf(patientIds), List.copyOf(patientNames), List.copyOf(studyUids), previews, warnings);
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "unknown-series";
    }

    private static class MutableSeries {
        private final DicomMetadataService.DicomMetadata metadata;
        private int fileCount;
        private long totalBytes;
        private String previewBase64;
        private MutableSeries(DicomMetadataService.DicomMetadata metadata) { this.metadata = metadata; }
        private SeriesPreview toPreview() {
            return new SeriesPreview(metadata.seriesInstanceUid(), metadata.seriesNumber(),
                metadata.seriesDescription(), metadata.modality(), metadata.studyDate(), fileCount, totalBytes,
                previewBase64);
        }
    }

    public record SeriesPreview(String seriesInstanceUid, Integer seriesNumber, String description,
                                String modality, String studyDate, int fileCount, long totalBytes,
                                String previewBase64) { }
    public record ArchiveAnalysis(String archiveType, int totalFiles, int dicomFiles,
                                  List<String> patientIds, List<String> patientNames, List<String> studyInstanceUids,
                                  List<SeriesPreview> series, List<String> warnings) { }
}
