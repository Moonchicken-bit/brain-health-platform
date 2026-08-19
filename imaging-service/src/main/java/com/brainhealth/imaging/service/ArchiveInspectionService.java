package com.brainhealth.imaging.service;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

@Service
public class ArchiveInspectionService {
    private static final int MAX_FILES = 100_000;
    private static final long MAX_SINGLE_FILE = 5L * 1024 * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024 * 1024;
    private static final long MAX_RATIO = 1_000;

    public InspectionSummary inspect(Path archivePath, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        try {
            return lower.endsWith(".zip") ? inspectZip(archivePath)
                : lower.endsWith(".rar") ? inspectRar(archivePath)
                : new InspectionSummary("NOT_ARCHIVE", 1, Files.size(archivePath), Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("压缩包损坏、加密或格式不受支持：" + e.getMessage(), e);
        }
    }

    public ExtractionSummary extract(Path archivePath, String fileName, Path destination) {
        InspectionSummary inspection = inspect(archivePath, fileName);
        try {
            Files.createDirectories(destination);
            if ("ZIP".equals(inspection.archiveType())) {
                extractZip(archivePath, destination);
            } else if ("RAR".equals(inspection.archiveType())) {
                extractRar(archivePath, destination);
            } else {
                throw new IllegalArgumentException("仅支持 ZIP 或 RAR 压缩包");
            }
            return scanExtracted(destination, inspection);
        } catch (Exception e) {
            throw new IllegalArgumentException("压缩包解压失败：" + e.getMessage(), e);
        }
    }

    private InspectionSummary inspectZip(Path path) throws IOException {
        int count = 0;
        long total = 0;
        Map<String, Integer> types = new TreeMap<>();
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                validateName(entry.getName());
                if (entry.isDirectory()) continue;
                count++;
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                validateLimits(count, size, total);
                if (compressed > 0 && size > 100L * 1024 * 1024 && size / compressed > MAX_RATIO) {
                    throw new IllegalArgumentException("疑似压缩炸弹：" + entry.getName());
                }
                total = Math.addExact(total, size);
                incrementType(types, entry.getName());
            }
        }
        return new InspectionSummary("ZIP", count, total, types);
    }

    private InspectionSummary inspectRar(Path path) throws Exception {
        int count = 0;
        long total = 0;
        Map<String, Integer> types = new TreeMap<>();
        try (Archive archive = new Archive(path.toFile())) {
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                String name = header.getFileName();
                validateName(name);
                if (header.isDirectory()) continue;
                count++;
                long size = header.getFullUnpackSize();
                validateLimits(count, size, total);
                total = Math.addExact(total, size);
                incrementType(types, name);
            }
        }
        return new InspectionSummary("RAR", count, total, types);
    }

    private void extractZip(Path archivePath, Path destination) throws IOException {
        try (ZipFile zip = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                Path target = safeTarget(destination, entry.getName());
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    copyLimited(in, out, entry.getSize());
                }
            }
        }
    }

    private void extractRar(Path archivePath, Path destination) throws Exception {
        try (Archive archive = new Archive(archivePath.toFile())) {
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (header.isDirectory()) continue;
                Path target = safeTarget(destination, header.getFileName());
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    archive.extractFile(header, out);
                }
                if (Files.size(target) > MAX_SINGLE_FILE) {
                    throw new IllegalArgumentException("单个解压文件过大：" + header.getFileName());
                }
            }
        }
    }

    private ExtractionSummary scanExtracted(Path destination, InspectionSummary inspection) throws IOException {
        List<ExtractedFile> files = new ArrayList<>();
        try (var paths = Files.walk(destination)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = destination.relativize(path).toString().replace('\\', '/');
                long size = Files.size(path);
                String type = detectType(path, relative);
                files.add(new ExtractedFile(relative, size, type));
            }
        }
        return new ExtractionSummary(inspection.archiveType(), files.size(),
            files.stream().mapToLong(ExtractedFile::size).sum(), List.copyOf(files));
    }

    private static String detectType(Path path, String name) throws IOException {
        String byName = typeFromName(name);
        if (!"OTHER".equals(byName)) return byName;
        if (Files.size(path) >= 132) {
            byte[] marker = new byte[4];
            try (InputStream in = Files.newInputStream(path)) {
                if (in.skip(128) == 128 && in.read(marker) == 4
                        && Arrays.equals(marker, new byte[]{'D', 'I', 'C', 'M'})) {
                    return "DICOM";
                }
            }
        }
        return "OTHER";
    }

    private static Path safeTarget(Path destination, String name) {
        validateName(name);
        Path root = destination.toAbsolutePath().normalize();
        Path target = root.resolve(name.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("压缩包包含越界路径：" + name);
        return target;
    }

    private static void copyLimited(InputStream in, OutputStream out, long expectedSize) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long written = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            written += read;
            if (written > MAX_SINGLE_FILE || (expectedSize >= 0 && written > expectedSize)) {
                throw new IllegalArgumentException("解压文件大小异常");
            }
            out.write(buffer, 0, read);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("压缩包包含无效文件名");
        }
        String unix = name.replace('\\', '/');
        Path normalized = Paths.get(unix).normalize();
        if (normalized.isAbsolute() || unix.startsWith("/") || unix.matches("^[A-Za-z]:.*")
                || normalized.startsWith("..")) {
            throw new IllegalArgumentException("压缩包包含越界路径：" + name);
        }
    }

    private static void validateLimits(int count, long size, long currentTotal) {
        if (count > MAX_FILES) throw new IllegalArgumentException("解压文件数量超过 " + MAX_FILES);
        if (size < 0 || size > MAX_SINGLE_FILE) throw new IllegalArgumentException("单个解压文件过大");
        if (currentTotal > MAX_TOTAL_SIZE - size) throw new IllegalArgumentException("解压总大小超过 50GB");
    }

    private static void incrementType(Map<String, Integer> types, String name) {
        types.merge(typeFromName(name), 1, Integer::sum);
    }

    private static String typeFromName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".dcm") ? "DICOM"
            : lower.endsWith(".nii") || lower.endsWith(".nii.gz") ? "NIFTI"
            : lower.endsWith(".pdf") ? "PDF"
            : lower.matches(".*\\.(png|jpg|jpeg|tif|tiff)$") ? "IMAGE"
            : lower.matches(".*\\.(xls|xlsx|csv)$") ? "TABLE" : "OTHER";
    }

    public record InspectionSummary(String archiveType, int fileCount, long uncompressedSize,
                                    Map<String, Integer> fileTypes) { }
    public record ExtractedFile(String relativePath, long size, String fileType) { }
    public record ExtractionSummary(String archiveType, int fileCount, long uncompressedSize,
                                    List<ExtractedFile> files) { }
}
