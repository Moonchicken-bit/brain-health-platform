package com.brainhealth.imaging.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class DicomMetadataService {
    private static final Set<String> LONG_VR = Set.of("OB", "OD", "OF", "OL", "OV", "OW", "SQ", "SV", "UC", "UN", "UR", "UT", "UV");
    private static final Map<Integer, String> TAGS = Map.ofEntries(
        Map.entry(0x00080020, "studyDate"),
        Map.entry(0x00080030, "studyTime"),
        Map.entry(0x00080060, "modality"),
        Map.entry(0x00081030, "studyDescription"),
        Map.entry(0x0008103E, "seriesDescription"),
        Map.entry(0x00100010, "patientName"),
        Map.entry(0x00100020, "patientId"),
        Map.entry(0x00100030, "patientBirthDate"),
        Map.entry(0x00100040, "patientSex"),
        Map.entry(0x0020000D, "studyInstanceUid"),
        Map.entry(0x0020000E, "seriesInstanceUid"),
        Map.entry(0x00200011, "seriesNumber"),
        Map.entry(0x00200013, "instanceNumber")
    );

    public DicomMetadata read(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] data = input.readNBytes(8 * 1024 * 1024);
            int offset = hasPreamble(data) ? 132 : 0;
            boolean explicitVr = true;
            while (offset + 8 <= data.length) {
                int group = readUnsignedShortLE(data, offset);
                int element = readUnsignedShortLE(data, offset + 2);
                offset += 4;
                int tag = (group << 16) | element;
                if (tag == 0x7FE00010) break;
                long valueLength;
                String vr;
                byte first = data[offset];
                byte second = data[offset + 1];
                if (isAsciiVr(first, second)) {
                    vr = new String(new byte[]{first, second}, StandardCharsets.US_ASCII);
                    offset += 2;
                    if (LONG_VR.contains(vr)) {
                        if (offset + 6 > data.length) break;
                        offset += 2;
                        valueLength = readUnsignedIntLE(data, offset);
                        offset += 4;
                    } else {
                        valueLength = readUnsignedShortLE(data, offset);
                        offset += 2;
                    }
                } else {
                    explicitVr = false;
                    valueLength = readUnsignedIntLE(data, offset);
                    offset += 4;
                    vr = "";
                }
                if (valueLength == 0xFFFFFFFFL || valueLength > data.length - offset) break;
                String key = TAGS.get(tag);
                if (key != null && valueLength <= 4096) {
                    values.put(key, cleanText(Arrays.copyOfRange(data, offset, offset + (int) valueLength)));
                }
                offset += (int) valueLength;
                if (!explicitVr && group == 0x0002) explicitVr = true;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取 DICOM 元数据：" + path.getFileName(), e);
        }
        return new DicomMetadata(
            values.get("patientId"), values.get("patientName"), values.get("patientBirthDate"),
            values.get("patientSex"), values.get("studyDate"), values.get("studyTime"),
            values.get("modality"), values.get("studyDescription"), values.get("seriesDescription"),
            values.get("studyInstanceUid"), values.get("seriesInstanceUid"),
            parseInteger(values.get("seriesNumber")), parseInteger(values.get("instanceNumber"))
        );
    }

    private static boolean hasPreamble(byte[] data) {
        return data.length >= 132 && data[128] == 'D' && data[129] == 'I'
            && data[130] == 'C' && data[131] == 'M';
    }

    private static boolean isAsciiVr(byte first, byte second) {
        return first >= 'A' && first <= 'Z' && second >= 'A' && second <= 'Z';
    }

    private static int readUnsignedShortLE(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }

    private static long readUnsignedIntLE(byte[] data, int offset) {
        return Integer.toUnsignedLong(readUnsignedShortLE(data, offset)
            | (readUnsignedShortLE(data, offset + 2) << 16));
    }

    private static String cleanText(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.UTF_8).replace("\0", "").trim();
        return value.replace('^', ' ');
    }

    private static Integer parseInteger(String value) {
        try { return value == null ? null : Integer.valueOf(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    public record DicomMetadata(
        String patientId, String patientName, String patientBirthDate, String patientSex,
        String studyDate, String studyTime, String modality, String studyDescription,
        String seriesDescription, String studyInstanceUid, String seriesInstanceUid,
        Integer seriesNumber, Integer instanceNumber) { }
}
