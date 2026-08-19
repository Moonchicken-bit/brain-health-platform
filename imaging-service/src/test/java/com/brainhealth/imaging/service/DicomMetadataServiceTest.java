package com.brainhealth.imaging.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DicomMetadataServiceTest {
    @TempDir Path temp;

    @Test
    void readsCoreMetadataFromExplicitVrDicom() throws Exception {
        Path dicom = temp.resolve("IM000001");
        Files.write(dicom, dicomBytes());
        var metadata = new DicomMetadataService().read(dicom);
        assertEquals("P001", metadata.patientId());
        assertEquals("ZHOU YIN LAN", metadata.patientName());
        assertEquals("MR", metadata.modality());
        assertEquals("1.2.3.4.5", metadata.seriesInstanceUid());
        assertEquals(3, metadata.seriesNumber());
    }

    @Test
    void archiveAnalysisGroupsInstancesIntoSeries() throws Exception {
        Path zip = temp.resolve("study.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 1; i <= 2; i++) {
                output.putNextEntry(new ZipEntry("PA/ST/SE/IM00000" + i));
                output.write(dicomBytes());
                output.closeEntry();
            }
        }
        var service = new ArchiveAnalysisService(new ArchiveInspectionService(), new DicomMetadataService());
        var result = service.analyze(zip, "study.zip", temp.resolve("out"));
        assertEquals(2, result.dicomFiles());
        assertEquals(1, result.series().size());
        assertEquals(2, result.series().get(0).fileCount());
        assertEquals("P001", result.patientIds().get(0));
    }

    @Test
    void analyzesProvidedRealRarWhenExplicitlyEnabled() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("realRarTest"));
        Path rar = Paths.get("../../ZHOU YIN LAN.rar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(rar));
        Path output = temp.resolve("real-rar");
        var service = new ArchiveAnalysisService(new ArchiveInspectionService(), new DicomMetadataService());
        var result = service.analyze(rar, rar.getFileName().toString(), output);
        assertTrue(result.dicomFiles() > 0, "真实压缩包应识别出 DICOM 文件");
        assertFalse(result.series().isEmpty(), "真实压缩包应形成影像序列");
    }

    private static byte[] dicomBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[128]);
        output.write("DICM".getBytes(StandardCharsets.US_ASCII));
        writeText(output, 0x0010, 0x0020, "LO", "P001");
        writeText(output, 0x0010, 0x0010, "PN", "ZHOU^YIN^LAN");
        writeText(output, 0x0008, 0x0060, "CS", "MR");
        writeText(output, 0x0020, 0x000D, "UI", "1.2.3");
        writeText(output, 0x0020, 0x000E, "UI", "1.2.3.4.5");
        writeText(output, 0x0020, 0x0011, "IS", "3");
        writeText(output, 0x0008, 0x103E, "LO", "T1 MPRAGE");
        return output.toByteArray();
    }

    private static void writeText(OutputStream output, int group, int element, String vr, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if ((bytes.length & 1) == 1) {
            byte[] padded = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            padded[padded.length - 1] = ' ';
            bytes = padded;
        }
        writeShort(output, group);
        writeShort(output, element);
        output.write(vr.getBytes(StandardCharsets.US_ASCII));
        writeShort(output, bytes.length);
        output.write(bytes);
    }

    private static void writeShort(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }
}
