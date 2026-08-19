package com.brainhealth.lab.service;

import com.brainhealth.lab.entity.*;
import com.brainhealth.lab.repository.*;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipFile;

@Service
public class LabReportParsingService {
    private static final int MAX_FILES = 10_000;
    private static final long MAX_TOTAL = 5L * 1024 * 1024 * 1024;
    private final LabReportUploadRepository uploadRepo;
    private final LabTestItemRepository testRepo;
    private final LabResultRepository resultRepo;

    public LabReportParsingService(LabReportUploadRepository uploadRepo, LabTestItemRepository testRepo,
                                   LabResultRepository resultRepo) {
        this.uploadRepo = uploadRepo;
        this.testRepo = testRepo;
        this.resultRepo = resultRepo;
    }

    public Preview preview(String uploadId) {
        LabReportUpload upload = uploadRepo.findById(uploadId)
            .orElseThrow(() -> new IllegalArgumentException("检验报告不存在"));
        Path source = Paths.get(upload.getStoragePath()).toAbsolutePath().normalize();
        Path work = null;
        try {
            work = Files.createTempDirectory("lab-report-parse-");
            List<Path> tables = collectTables(source, upload.getOriginalName(), work);
            List<RawRow> rows = new ArrayList<>();
            for (Path table : tables) rows.addAll(parseTable(table));
            List<LabTestItem> tests = testRepo.findAll();
            List<Candidate> candidates = rows.stream().map(row -> map(row, tests)).toList();
            List<String> warnings = new ArrayList<>();
            if (tables.isEmpty()) warnings.add("未发现 CSV/XLS/XLSX 表格；图片和 PDF 需进入文字识别流程");
            if (candidates.stream().anyMatch(candidate -> candidate.labTestId() == null)) {
                warnings.add("存在未匹配到检验字典的项目，请医生选择项目后再确认");
            }
            upload.setStatus("PARSED");
            uploadRepo.save(upload);
            return new Preview(uploadId, tables.size(), candidates, warnings);
        } catch (Exception e) {
            upload.setStatus("PARSE_FAILED");
            uploadRepo.save(upload);
            throw new IllegalArgumentException("检验报告解析失败：" + e.getMessage(), e);
        } finally {
            deleteTree(work);
        }
    }

    @Transactional
    public List<LabResult> confirm(String uploadId, List<Candidate> candidates) {
        LabReportUpload upload = uploadRepo.findById(uploadId)
            .orElseThrow(() -> new IllegalArgumentException("检验报告不存在"));
        if (candidates == null || candidates.isEmpty()) throw new IllegalArgumentException("没有可确认的检验结果");
        List<LabResult> results = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.labTestId() == null || candidate.value() == null || candidate.value().isBlank()) continue;
            LabResult result = new LabResult();
            result.setSubjectId(upload.getSubjectId());
            result.setSessionId(upload.getSessionId());
            result.setLabTestId(candidate.labTestId());
            result.setResult(candidate.value());
            result.setUnit(candidate.unit());
            result.setReferenceRange(candidate.referenceRange());
            result.setIsAbnormal(parseAbnormal(candidate.abnormalFlag()));
            result.setCollectionDate(parseDate(candidate.collectionDate()));
            result.setNotes("由检验报告 " + upload.getOriginalName() + " 自动解析，人工确认导入");
            results.add(result);
        }
        if (results.isEmpty()) throw new IllegalArgumentException("没有已匹配且包含结果值的项目");
        upload.setStatus("CONFIRMED");
        uploadRepo.save(upload);
        return resultRepo.saveAll(results);
    }

    private List<Path> collectTables(Path source, String originalName, Path work) throws Exception {
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") || lower.endsWith(".xls") || lower.endsWith(".xlsx")) return List.of(source);
        if (lower.endsWith(".zip")) extractZip(source, work);
        else if (lower.endsWith(".rar")) extractRar(source, work);
        else return List.of();
        try (var paths = Files.walk(work)) {
            return paths.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".csv") || name.endsWith(".xls") || name.endsWith(".xlsx");
            }).limit(MAX_FILES).toList();
        }
    }

    private List<RawRow> parseTable(Path path) throws IOException {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")
            ? parseCsv(path) : parseWorkbook(path);
    }

    private List<RawRow> parseCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, detectCharset(path));
        if (lines.isEmpty()) return List.of();
        List<String> headers = splitCsv(lines.get(0));
        List<RawRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsv(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) row.put(headers.get(c), c < values.size() ? values.get(c) : "");
            RawRow parsed = fromColumns(row);
            if (parsed != null) rows.add(parsed);
        }
        return rows;
    }

    private List<RawRow> parseWorkbook(Path path) throws IOException {
        List<RawRow> rows = new ArrayList<>();
        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (Sheet sheet : workbook) {
                if (sheet.getPhysicalNumberOfRows() < 2) continue;
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) headers.add(formatter.formatCellValue(cell).trim());
                for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row dataRow = sheet.getRow(r);
                    if (dataRow == null) continue;
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = dataRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.put(headers.get(c), cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    RawRow parsed = fromColumns(values);
                    if (parsed != null) rows.add(parsed);
                }
            }
        }
        return rows;
    }

    private RawRow fromColumns(Map<String, String> row) {
        String name = find(row, "项目", "检验项目", "项目名称", "test", "analyte", "name");
        String value = find(row, "结果", "检验结果", "result", "value");
        if ((name == null || name.isBlank()) && (value == null || value.isBlank())) return null;
        return new RawRow(name, value, find(row, "单位", "unit"),
            find(row, "参考范围", "参考值", "reference", "range"),
            find(row, "异常", "提示", "flag", "abnormal"),
            find(row, "日期", "检验日期", "采样日期", "date"));
    }

    private Candidate map(RawRow row, List<LabTestItem> tests) {
        String normalized = normalize(row.name());
        LabTestItem exact = tests.stream().filter(test -> normalize(test.getName()).equals(normalized)).findFirst().orElse(null);
        if (exact == null && !normalized.isBlank()) {
            exact = tests.stream().filter(test -> normalize(test.getName()).contains(normalized)
                || normalized.contains(normalize(test.getName()))).findFirst().orElse(null);
        }
        return new Candidate(exact == null ? null : exact.getId(), row.name(),
            exact == null ? null : exact.getName(), row.value(), first(row.unit(), exact == null ? null : exact.getUnit()),
            row.referenceRange(), row.abnormalFlag(), row.collectionDate(), exact == null ? 0.0 : 1.0);
    }

    private static void extractZip(Path source, Path work) throws IOException {
        long[] total = {0}; int[] count = {0};
        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                Path target = safeTarget(work, entry.getName());
                if (++count[0] > MAX_FILES || (total[0] += Math.max(0, entry.getSize())) > MAX_TOTAL)
                    throw new IllegalArgumentException("压缩包内容超过安全限制");
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, target); }
            }
        }
    }

    private static void extractRar(Path source, Path work) throws Exception {
        long total = 0; int count = 0;
        try (Archive archive = new Archive(source.toFile())) {
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (header.isDirectory()) continue;
                Path target = safeTarget(work, header.getFileName());
                if (++count > MAX_FILES || (total += header.getFullUnpackSize()) > MAX_TOTAL)
                    throw new IllegalArgumentException("压缩包内容超过安全限制");
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) { archive.extractFile(header, out); }
            }
        }
    }

    private static Path safeTarget(Path root, String name) {
        Path target = root.resolve(name.replace('\\', '/')).normalize();
        if (!target.startsWith(root) || Paths.get(name.replace('\\', '/')).isAbsolute())
            throw new IllegalArgumentException("压缩包包含越界路径");
        return target;
    }

    private static Charset detectCharset(Path path) throws IOException {
        byte[] head = Files.readAllBytes(path);
        if (head.length >= 3 && (head[0] & 0xff) == 0xef && (head[1] & 0xff) == 0xbb) return StandardCharsets.UTF_8;
        String utf8 = new String(head, StandardCharsets.UTF_8);
        return utf8.indexOf('\uFFFD') >= 0 ? Charset.forName("GB18030") : StandardCharsets.UTF_8;
    }

    private static List<String> splitCsv(String line) {
        List<String> values = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) { values.add(current.toString().trim()); current.setLength(0); }
            else current.append(ch);
        }
        values.add(current.toString().trim()); return values;
    }

    private static String find(Map<String, String> row, String... names) {
        for (var entry : row.entrySet()) {
            String header = normalize(entry.getKey());
            for (String name : names) if (header.contains(normalize(name))) return entry.getValue();
        }
        return null;
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_()（）\\-/:：]", "");
    }
    private static String first(String a, String b) { return a == null || a.isBlank() ? b : a; }
    private static Boolean parseAbnormal(String flag) {
        if (flag == null) return false;
        String value = flag.toUpperCase(Locale.ROOT);
        return value.contains("高") || value.contains("低") || value.contains("异常")
            || value.contains("H") || value.contains("L") || value.contains("*");
    }
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.replace('/', '-').substring(0, 10)); } catch (Exception ignored) { return null; }
    }
    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }

    private record RawRow(String name, String value, String unit, String referenceRange,
                          String abnormalFlag, String collectionDate) { }
    public record Candidate(Long labTestId, String sourceName, String matchedName, String value, String unit,
                            String referenceRange, String abnormalFlag, String collectionDate, double confidence) { }
    public record Preview(String uploadId, int tableCount, List<Candidate> candidates, List<String> warnings) { }
}
