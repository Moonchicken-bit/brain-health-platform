package com.brainhealth.adni.service;

import com.brainhealth.adni.entity.AdniSubject;
import com.brainhealth.adni.repository.AdniSubjectRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdniImportService {
    private final AdniSubjectRepository repository;

    public AdniImportService(AdniSubjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 ADNI CSV 或 Excel 文件");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> rows;
        try {
            rows = name.endsWith(".csv") ? readCsv(file) : readWorkbook(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("ADNI 文件读取失败：" + e.getMessage(), e);
        }
        if (rows.size() < 2) throw new IllegalArgumentException("文件没有可导入的数据行");
        Map<String, Integer> columns = indexHeaders(rows.get(0));
        Integer idColumn = find(columns, "RID", "PTID", "SUBJECT_ID", "ADNI_SUBJECT_ID");
        if (idColumn == null) throw new IllegalArgumentException("缺少 RID、PTID 或 ADNI_SUBJECT_ID 列");

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            String identifier = cell(row, idColumn);
            if (identifier.isBlank()) {
                skipped++;
                continue;
            }
            try {
                AdniSubject subject = repository.findByAdniSubjectId(identifier).orElseGet(AdniSubject::new);
                boolean existing = subject.getId() != null;
                subject.setAdniSubjectId(identifier);
                subject.setDiagnosis(normalizeDiagnosis(value(row, columns, "DX", "DIAGNOSIS", "DX_bl")));
                subject.setSex(normalizeSex(value(row, columns, "PTGENDER", "SEX", "GENDER")));
                subject.setAge(integer(value(row, columns, "AGE", "PTAGE")));
                subject.setEducationYears(integer(value(row, columns, "PTEDUCAT", "EDUCATION_YEARS", "EDUCATION")));
                subject.setApoeGenotype(apoe(row, columns));
                subject.setHasImaging(bool(value(row, columns, "HAS_IMAGING", "IMAGING")));
                subject.setHasGenetics(bool(value(row, columns, "HAS_GENETICS", "GENETICS")));
                repository.save(subject);
                if (existing) updated++; else imported++;
            } catch (RuntimeException e) {
                errors.add("第 " + (rowIndex + 1) + " 行：" + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", errors.isEmpty() ? "completed" : "partial");
        result.put("importedCount", imported);
        result.put("updatedCount", updated);
        result.put("skippedCount", skipped);
        result.put("errors", errors.stream().limit(100).toList());
        return result;
    }

    private List<List<String>> readWorkbook(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int last = Math.max(0, row.getLastCellNum());
                for (int column = 0; column < last; column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)));
                }
                rows.add(values);
            }
        }
        return rows;
    }

    private List<List<String>> readCsv(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) rows.add(parseCsvLine(line));
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                result.add(value.toString().trim());
                value.setLength(0);
            } else value.append(current);
        }
        result.add(value.toString().trim());
        return result;
    }

    private Map<String, Integer> indexHeaders(List<String> header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
            result.putIfAbsent(key, i);
        }
        return result;
    }

    private Integer find(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name.toUpperCase(Locale.ROOT));
            if (index != null) return index;
        }
        return null;
    }

    private String value(List<String> row, Map<String, Integer> columns, String... names) {
        Integer index = find(columns, names);
        return index == null ? "" : cell(row, index);
    }

    private String cell(List<String> row, int index) {
        return index < row.size() && row.get(index) != null ? row.get(index).trim() : "";
    }

    private Integer integer(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeDiagnosis(String value) {
        if (value == null || value.isBlank()) return null;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("MCI")) return "MCI";
        if (upper.equals("AD") || upper.contains("DEMENTIA")) return "AD";
        if (upper.equals("CN") || upper.equals("NL") || upper.contains("NORMAL")) return "CN";
        return value.trim();
    }

    private String normalizeSex(String value) {
        if (value == null || value.isBlank()) return null;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("M") || "男".equals(value.trim())) return "M";
        if (upper.startsWith("F") || "女".equals(value.trim())) return "F";
        return value.substring(0, Math.min(2, value.length()));
    }

    private String apoe(List<String> row, Map<String, Integer> columns) {
        String direct = value(row, columns, "APOE", "APOE_GENOTYPE", "APOE4");
        if (!direct.isBlank()) return direct.replace("/", "").replace("ε", "E").toUpperCase(Locale.ROOT);
        String a1 = value(row, columns, "APGEN1");
        String a2 = value(row, columns, "APGEN2");
        return a1.isBlank() || a2.isBlank() ? null : "E" + a1 + "E" + a2;
    }

    private Boolean bool(String value) {
        if (value == null || value.isBlank()) return false;
        return List.of("1", "Y", "YES", "TRUE", "有", "是")
                .contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
