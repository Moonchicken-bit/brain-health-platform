package com.brainhealth.scale.service;

import com.brainhealth.common.exception.BusinessException;
import com.brainhealth.common.exception.ErrorCode;
import com.brainhealth.common.model.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
public class ClinicalFieldDictionaryService {
    private final ObjectMapper objectMapper;
    private List<JsonNode> fields = List.of();
    private JsonNode root;

    @Value("${scale.clinical-field-dictionary.path:clinical_field_dictionary.json}")
    private String dictionaryPath;

    public ClinicalFieldDictionaryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        String[] candidates = {
            dictionaryPath, "clinical_field_dictionary.json",
            "../clinical_field_dictionary.json", "../../clinical_field_dictionary.json"
        };
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (!file.isFile()) continue;
            try {
                root = objectMapper.readTree(file);
                if (root.path("fieldCount").asInt() != 10290 || !root.path("fields").isArray()) {
                    throw new IllegalStateException("字段字典数量或结构不正确");
                }
                List<JsonNode> loaded = new ArrayList<>(10290);
                root.path("fields").forEach(loaded::add);
                fields = Collections.unmodifiableList(loaded);
                return;
            } catch (Exception e) {
                throw new IllegalStateException("无法加载临床字段字典：" + file.getAbsolutePath(), e);
            }
        }
        throw new IllegalStateException("未找到 clinical_field_dictionary.json");
    }

    public Map<String, Object> summary() {
        ensureLoaded();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", root.path("schemaVersion").asText());
        result.put("source", root.path("source").asText());
        result.put("fieldCount", fields.size());
        result.put("categoryCounts", objectMapper.convertValue(root.path("categoryCounts"), Map.class));
        result.put("categories", fields.stream().map(f -> f.path("category").asText()).distinct().sorted().toList());
        return result;
    }

    public PageResult<JsonNode> search(
            String category, String formCode, String visitCode, String keyword, int page, int size) {
        ensureLoaded();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 500);
        String query = normalize(keyword);
        List<JsonNode> matched = fields.stream()
            .filter(f -> blank(category) || category.equals(f.path("category").asText()))
            .filter(f -> blank(formCode) || formCode.equalsIgnoreCase(f.path("formCode").asText()))
            .filter(f -> blank(visitCode) || visitCode.equalsIgnoreCase(f.path("visitCode").asText()))
            .filter(f -> query.isEmpty() || normalize(f.path("code").asText()).contains(query)
                || normalize(f.path("name").asText()).contains(query))
            .toList();
        int from = Math.min((safePage - 1) * safeSize, matched.size());
        int to = Math.min(from + safeSize, matched.size());
        return PageResult.of(safePage, safeSize, matched.size(), matched.subList(from, to));
    }

    private void ensureLoaded() {
        if (fields.size() != 10290) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "临床字段字典未完整加载");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
