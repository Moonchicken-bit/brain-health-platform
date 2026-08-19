package com.brainhealth.imaging.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class DicomPreviewService {
    private static final String EXPLICIT_LE = "1.2.840.10008.1.2.1";
    private static final String IMPLICIT_LE = "1.2.840.10008.1.2";

    public byte[] createPng(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        Parsed parsed = parse(data);
        BufferedImage source = render(parsed, data);
        int maxSide = 640;
        double scale = Math.min(1d, maxSide / (double) Math.max(source.getWidth(), source.getHeight()));
        BufferedImage output = source;
        if (scale < 1d) {
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(output, "png", bytes);
            return bytes.toByteArray();
        }
    }

    private Parsed parse(byte[] data) throws IOException {
        int offset = hasPreamble(data) ? 132 : 0;
        boolean explicit = true;
        boolean littleEndian = true;
        Parsed result = new Parsed();

        while (offset + 8 <= data.length) {
            int group = ushort(data, offset, littleEndian);
            int element = ushort(data, offset + 2, littleEndian);
            int tag = (group << 16) | element;
            offset += 4;

            boolean meta = group == 0x0002;
            boolean currentExplicit = meta || explicit;
            long length;
            if (currentExplicit && isVr(data, offset)) {
                String vr = ascii(data, offset, 2);
                offset += 2;
                if (isLongVr(vr)) {
                    offset += 2;
                    length = uint(data, offset, littleEndian);
                    offset += 4;
                } else {
                    length = ushort(data, offset, littleEndian);
                    offset += 2;
                }
            } else {
                length = uint(data, offset, littleEndian);
                offset += 4;
            }

            if (length == 0xffffffffL) {
                throw new IOException("暂不支持封装或未定义长度的 DICOM 像素数据");
            }
            if (length < 0 || length > data.length - offset) {
                throw new IOException("DICOM 数据长度无效");
            }

            if (tag == 0x00020010) {
                String syntax = text(data, offset, (int) length);
                if (IMPLICIT_LE.equals(syntax)) {
                    explicit = false;
                } else if (EXPLICIT_LE.equals(syntax)) {
                    explicit = true;
                } else if ("1.2.840.10008.1.2.2".equals(syntax)) {
                    littleEndian = false;
                    explicit = true;
                } else if (!syntax.isBlank()) {
                    throw new IOException("暂不支持压缩传输语法: " + syntax);
                }
            } else if (tag == 0x00280002) result.samples = numeric(data, offset, (int) length, littleEndian);
            else if (tag == 0x00280004) result.photometric = text(data, offset, (int) length);
            else if (tag == 0x00280008) result.frames = decimalInt(text(data, offset, (int) length), 1);
            else if (tag == 0x00280010) result.rows = numeric(data, offset, (int) length, littleEndian);
            else if (tag == 0x00280011) result.columns = numeric(data, offset, (int) length, littleEndian);
            else if (tag == 0x00280100) result.bitsAllocated = numeric(data, offset, (int) length, littleEndian);
            else if (tag == 0x00280101) result.bitsStored = numeric(data, offset, (int) length, littleEndian);
            else if (tag == 0x00280103) result.signed = numeric(data, offset, (int) length, littleEndian) == 1;
            else if (tag == 0x00281050) result.windowCenter = decimal(text(data, offset, (int) length));
            else if (tag == 0x00281051) result.windowWidth = decimal(text(data, offset, (int) length));
            else if (tag == 0x00281052) result.intercept = decimal(text(data, offset, (int) length));
            else if (tag == 0x00281053) result.slope = decimal(text(data, offset, (int) length));
            else if (tag == 0x7fe00010) {
                result.pixelOffset = offset;
                result.pixelLength = (int) length;
                result.littleEndian = littleEndian;
                break;
            }
            offset += (int) length;
        }

        if (result.rows <= 0 || result.columns <= 0 || result.pixelOffset <= 0) {
            throw new IOException("DICOM 文件没有可显示的像素数据");
        }
        return result;
    }

    private BufferedImage render(Parsed parsed, byte[] data) throws IOException {
        int samples = Math.max(1, parsed.samples);
        int bytesPerSample = Math.max(1, parsed.bitsAllocated / 8);
        long frameSize = (long) parsed.rows * parsed.columns * samples * bytesPerSample;
        if (frameSize <= 0 || frameSize > parsed.pixelLength) throw new IOException("DICOM 像素数据不完整");
        int frame = Math.max(0, Math.min(parsed.frames - 1, parsed.frames / 2));
        int start = parsed.pixelOffset + (int) (frameSize * frame);

        if (samples >= 3 && bytesPerSample == 1) {
            BufferedImage image = new BufferedImage(parsed.columns, parsed.rows, BufferedImage.TYPE_INT_RGB);
            int cursor = start;
            for (int y = 0; y < parsed.rows; y++) {
                for (int x = 0; x < parsed.columns; x++) {
                    int r = Byte.toUnsignedInt(data[cursor++]);
                    int g = Byte.toUnsignedInt(data[cursor++]);
                    int b = Byte.toUnsignedInt(data[cursor++]);
                    cursor += Math.max(0, samples - 3);
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return image;
        }

        int count = parsed.rows * parsed.columns;
        double[] values = new double[count];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int cursor = start;
        int storedBits = parsed.bitsStored > 0 ? parsed.bitsStored : parsed.bitsAllocated;
        for (int index = 0; index < count; index++) {
            int raw;
            if (bytesPerSample == 1) {
                raw = Byte.toUnsignedInt(data[cursor++]);
            } else if (bytesPerSample == 2) {
                raw = ushort(data, cursor, parsed.littleEndian);
                cursor += 2;
                if (parsed.signed && storedBits < 32 && (raw & (1 << (storedBits - 1))) != 0) {
                    raw -= 1 << storedBits;
                }
            } else {
                throw new IOException("暂不支持 " + parsed.bitsAllocated + " 位 DICOM 像素");
            }
            double value = raw * parsed.slope + parsed.intercept;
            values[index] = value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double low = min;
        double high = max;
        if (parsed.windowWidth != null && parsed.windowWidth > 1 && parsed.windowCenter != null) {
            low = parsed.windowCenter - parsed.windowWidth / 2d;
            high = parsed.windowCenter + parsed.windowWidth / 2d;
        }
        if (high <= low) high = low + 1;
        boolean invert = parsed.photometric.toUpperCase(Locale.ROOT).contains("MONOCHROME1");
        BufferedImage image = new BufferedImage(parsed.columns, parsed.rows, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < parsed.rows; y++) {
            for (int x = 0; x < parsed.columns; x++) {
                int gray = (int) Math.round(255d * (values[y * parsed.columns + x] - low) / (high - low));
                gray = Math.max(0, Math.min(255, gray));
                if (invert) gray = 255 - gray;
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static boolean hasPreamble(byte[] data) {
        return data.length >= 132 && data[128] == 'D' && data[129] == 'I' && data[130] == 'C' && data[131] == 'M';
    }

    private static boolean isVr(byte[] data, int offset) {
        return offset + 1 < data.length && data[offset] >= 'A' && data[offset] <= 'Z'
            && data[offset + 1] >= 'A' && data[offset + 1] <= 'Z';
    }

    private static boolean isLongVr(String vr) {
        return switch (vr) {
            case "OB", "OD", "OF", "OL", "OV", "OW", "SQ", "SV", "UC", "UN", "UR", "UT", "UV" -> true;
            default -> false;
        };
    }

    private static int numeric(byte[] data, int offset, int length, boolean littleEndian) {
        if (length == 2) return ushort(data, offset, littleEndian);
        return decimalInt(text(data, offset, length), 0);
    }

    private static int ushort(byte[] data, int offset, boolean littleEndian) {
        return littleEndian
            ? Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8)
            : (Byte.toUnsignedInt(data[offset]) << 8) | Byte.toUnsignedInt(data[offset + 1]);
    }

    private static long uint(byte[] data, int offset, boolean littleEndian) {
        if (littleEndian) {
            return Integer.toUnsignedLong(ushort(data, offset, true) | (ushort(data, offset + 2, true) << 16));
        }
        return Integer.toUnsignedLong((ushort(data, offset, false) << 16) | ushort(data, offset + 2, false));
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static String text(byte[] data, int offset, int length) {
        return ascii(data, offset, length).replace("\0", "").trim();
    }

    private static int decimalInt(String value, int fallback) {
        try { return Integer.parseInt(firstValue(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private static Double decimal(String value) {
        try { return Double.valueOf(firstValue(value)); }
        catch (Exception ignored) { return null; }
    }

    private static String firstValue(String value) {
        int separator = value.indexOf('\\');
        return (separator >= 0 ? value.substring(0, separator) : value).trim();
    }

    private static final class Parsed {
        private int rows;
        private int columns;
        private int samples = 1;
        private int frames = 1;
        private int bitsAllocated = 16;
        private int bitsStored = 16;
        private boolean signed;
        private boolean littleEndian = true;
        private String photometric = "MONOCHROME2";
        private Double windowCenter;
        private Double windowWidth;
        private double intercept = 0d;
        private double slope = 1d;
        private int pixelOffset;
        private int pixelLength;
    }
}
