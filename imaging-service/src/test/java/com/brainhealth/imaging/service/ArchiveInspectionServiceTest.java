package com.brainhealth.imaging.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveInspectionServiceTest {
    private final ArchiveInspectionService service = new ArchiveInspectionService();

    @TempDir Path temp;

    @Test
    void inspectsSafeZip() throws Exception {
        Path zip = temp.resolve("safe.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("study/series/image.dcm"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        var result = service.inspect(zip, "safe.zip");
        assertEquals("ZIP", result.archiveType());
        assertEquals(1, result.fileCount());
        assertEquals(1, result.fileTypes().get("DICOM"));
    }

    @Test
    void rejectsZipSlipPath() throws Exception {
        Path zip = temp.resolve("malicious.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../outside.txt"));
            out.write(1);
            out.closeEntry();
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.inspect(zip, "malicious.zip"));
        assertTrue(error.getMessage().contains("越界路径"));
    }

    @Test
    void extractsAndDetectsExtensionlessDicom() throws Exception {
        Path zip = temp.resolve("dicom.zip");
        byte[] dicom = new byte[132];
        dicom[128] = 'D';
        dicom[129] = 'I';
        dicom[130] = 'C';
        dicom[131] = 'M';
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("PA/ST/SE/IM000001"));
            out.write(dicom);
            out.closeEntry();
        }
        Path extracted = temp.resolve("extracted");
        var result = service.extract(zip, "dicom.zip", extracted);
        assertEquals(1, result.fileCount());
        assertEquals("DICOM", result.files().get(0).fileType());
        assertTrue(Files.isRegularFile(extracted.resolve("PA/ST/SE/IM000001")));
    }

    @Test
    void inspectsProvidedRealRarSample() {
        Path rar = Paths.get("../../ZHOU YIN LAN.rar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(rar), "真实 RAR 验收样本不存在");
        var result = service.inspect(rar, rar.getFileName().toString());
        assertEquals("RAR", result.archiveType());
        assertTrue(result.fileCount() > 100);
        assertTrue(result.uncompressedSize() > 0);
    }
}
