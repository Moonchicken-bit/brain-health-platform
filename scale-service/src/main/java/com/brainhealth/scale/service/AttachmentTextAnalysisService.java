package com.brainhealth.scale.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.*;

@Service
public class AttachmentTextAnalysisService {
    private final VisitAttachmentService attachmentService;
    private final String language;
    private final String configuredDataPath;
    private final String executable;

    public AttachmentTextAnalysisService(
            VisitAttachmentService attachmentService,
            @Value("${brain-health.ocr.language:eng}") String language,
            @Value("${brain-health.ocr.data-path:}") String configuredDataPath,
            @Value("${brain-health.ocr.executable:tesseract}") String executable) {
        this.attachmentService = attachmentService;
        this.language = language;
        this.configuredDataPath = configuredDataPath;
        this.executable = executable;
    }

    public AnalysisResult analyze(String attachmentId) {
        var attachment = attachmentService.get(attachmentId);
        try {
            File file = attachmentService.loadContent(attachmentId).getFile();
            return analyzeFile(attachmentId, file, attachment.getContentType(), attachment.getOriginalName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("附件文字分析失败：" + e.getMessage(), e);
        }
    }

    AnalysisResult analyzeFile(String attachmentId, File file, String rawContentType, String rawName) {
        try {
            String contentType = Optional.ofNullable(rawContentType).orElse("").toLowerCase(Locale.ROOT);
            String name = Optional.ofNullable(rawName).orElse("").toLowerCase(Locale.ROOT);
            String text;
            String method;
            if (contentType.contains("pdf") || name.endsWith(".pdf")) {
                try (var document = Loader.loadPDF(file)) {
                    text = new PDFTextStripper().getText(document).trim();
                }
                if (text.length() >= 20) {
                    method = "PDF_TEXT";
                } else {
                    text = ocr(file);
                    method = "OCR";
                }
            } else if (contentType.startsWith("image/") || name.matches(".*\\.(png|jpg|jpeg|tif|tiff|bmp)$")) {
                text = ocr(file);
                method = "OCR";
            } else {
                throw new IllegalArgumentException("仅支持 PDF 或图片文字分析");
            }
            Map<String, String> fields = extractKeyValues(text);
            List<String> warnings = new ArrayList<>();
            if (text.isBlank()) warnings.add("未识别出文字，请改用清晰原图或人工录入");
            warnings.add("识别结果仅作为录入草稿，提交前必须由医生核对原始文件");
            return new AnalysisResult(attachmentId, method, language, text, fields, warnings);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("附件文字分析失败：" + e.getMessage(), e);
        }
    }

    private String ocr(File file) throws Exception {
        List<String> command = new ArrayList<>(List.of(
            executable, file.getAbsolutePath(), "stdout", "-l", language, "--psm", "3"));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(false);
        if (configuredDataPath != null && !configuredDataPath.isBlank()) {
            builder.environment().put("TESSDATA_PREFIX", configuredDataPath);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException unavailable) {
            throw new IllegalArgumentException("OCR 引擎未安装或不可执行，请安装 Tesseract 中文语言包", unavailable);
        }
        ExecutorService reader = Executors.newFixedThreadPool(2);
        try {
            Future<String> output = reader.submit(() ->
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> diagnostics = reader.submit(() ->
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalArgumentException("图片文字识别超时");
            }
            String text = output.get(10, TimeUnit.SECONDS).trim();
            String errors = diagnostics.get(10, TimeUnit.SECONDS).trim();
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("OCR 引擎执行失败：" + errors);
            }
            return text;
        } finally {
            reader.shutdownNow();
        }
    }

    static Map<String, String> extractKeyValues(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (text == null) return fields;
        for (String line : text.split("\\R")) {
            String clean = line.trim();
            int separator = firstSeparator(clean);
            if (separator > 0 && separator < clean.length() - 1) {
                String key = clean.substring(0, separator).trim();
                String value = clean.substring(separator + 1).trim();
                if (key.length() <= 80 && value.length() <= 500) fields.putIfAbsent(key, value);
            }
        }
        return fields;
    }

    private static int firstSeparator(String value) {
        int colon = value.indexOf(':');
        int chineseColon = value.indexOf('：');
        if (colon < 0) return chineseColon;
        if (chineseColon < 0) return colon;
        return Math.min(colon, chineseColon);
    }

    public record AnalysisResult(String attachmentId, String method, String language, String text,
                                 Map<String, String> fields, List<String> warnings) { }
}
