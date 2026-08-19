package com.brainhealth.search.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.security.DataScopeGuard;
import com.brainhealth.search.entity.SavedSearch;
import com.brainhealth.search.repository.SavedSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "lt", "gte", "lte", "contains");
    private static final Set<String> FIELDS = Set.of(
        "keyword", "subjectId", "diagnosis", "sex", "age", "educationYears", "cohort",
        "scale", "score", "gene", "variantType", "modality", "labTestId", "isAbnormal");

    private final JdbcTemplate jdbc;
    private final SavedSearchRepository savedRepo;
    private final DataScopeGuard scopeGuard;
    private final HttpServletRequest request;
    private final ObjectMapper mapper;

    public SearchController(JdbcTemplate jdbc, SavedSearchRepository savedRepo, DataScopeGuard scopeGuard,
                            HttpServletRequest request, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.savedRepo = savedRepo;
        this.scopeGuard = scopeGuard;
        this.request = request;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String diagnosis,
            @RequestParam(required = false) String gene,
            @RequestParam(required = false) String scale,
            @RequestParam(required = false) Double scoreMin,
            @RequestParam(required = false) Double scoreMax,
            @RequestParam(required = false) String modality,
            @RequestParam(required = false) String cohort,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Filter> filters = new ArrayList<>();
        add(filters, "keyword", "contains", firstNonBlank(subjectId, q));
        add(filters, "diagnosis", "contains", diagnosis);
        add(filters, "gene", "contains", gene);
        add(filters, "scale", "contains", scale);
        if (scoreMin != null) filters.add(new Filter("score", "gte", scoreMin));
        if (scoreMax != null) filters.add(new Filter("score", "lte", scoreMax));
        add(filters, "modality", "eq", modality);
        add(filters, "cohort", "eq", cohort);
        return ApiResponse.ok(execute(filters, "AND", institutionId, dateFrom, dateTo, page, size));
    }

    @PostMapping("/advanced")
    public ApiResponse<Map<String, Object>> advancedSearch(@RequestBody Map<String, Object> query) {
        Object rawFilters = query.get("filters");
        if (!(rawFilters instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("filters must be an array");
        }
        List<Filter> filters = new ArrayList<>();
        for (Object raw : collection) {
            if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("filter must be an object");
            String field = string(map.get("field"));
            String operator = string(map.get("operator")).toLowerCase(Locale.ROOT);
            if (!FIELDS.contains(field) || !OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("unsupported search field or operator");
            }
            Object value = map.get("value");
            if (value == null) throw new IllegalArgumentException("filter value is required");
            filters.add(new Filter(field, operator, value));
        }
        String operator = string(query.getOrDefault("operator", "AND")).toUpperCase(Locale.ROOT);
        if (!Set.of("AND", "OR").contains(operator)) throw new IllegalArgumentException("operator must be AND or OR");
        return ApiResponse.ok(execute(filters, operator, longValue(query.get("institutionId")),
            stringOrNull(query.get("dateFrom")), stringOrNull(query.get("dateTo")),
            integer(query.get("page"), 1), integer(query.get("size"), 20)));
    }

    private Map<String, Object> execute(List<Filter> filters, String joinOperator, Long institutionId,
                                        String dateFrom, String dateTo, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<Object> scopeArgs = new ArrayList<>();
        String scopeSql = scopeSql(scopeArgs);
        List<String> filterClauses = new ArrayList<>();
        List<Object> filterArgs = new ArrayList<>();
        for (Filter filter : filters) filterClauses.add(filterClause(filter, filterArgs));
        if (institutionId != null) { filterClauses.add("s.enrollment_institution_id=?"); filterArgs.add(institutionId); }
        if (hasText(dateFrom)) { filterClauses.add("s.enrollment_date>=?"); filterArgs.add(dateFrom); }
        if (hasText(dateTo)) { filterClauses.add("s.enrollment_date<=?"); filterArgs.add(dateTo); }

        String where = " WHERE 1=1" + scopeSql;
        if (!filterClauses.isEmpty()) where += " AND (" + String.join(" " + joinOperator + " ", filterClauses) + ")";
        List<Object> args = new ArrayList<>(scopeArgs);
        args.addAll(filterArgs);
        String select = "SELECT DISTINCT s.id,s.subject_code," +
            "CASE WHEN sx.code='1' THEN 'M' WHEN sx.code='2' THEN 'F' ELSE sx.code END sex," +
            "s.birth_date,s.education_years,s.status " +
            "FROM subject s LEFT JOIN sex_code sx ON sx.id=s.sex_code_id";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
            select + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT s.id) FROM subject s " +
            "LEFT JOIN sex_code sx ON sx.id=s.sex_code_id" + where, Long.class, args.toArray());

        List<String> matchedOn = filters.stream().map(Filter::field).distinct().toList();
        List<Map<String, Object>> records = rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", row.get("id"));
            result.put("subjectId", row.get("id"));
            result.put("subjectCode", row.get("subject_code"));
            result.put("status", row.get("status"));
            result.put("subjectInfo", Map.of(
                "sex", Objects.toString(row.get("sex"), ""),
                "dateOfBirth", Objects.toString(row.get("birth_date"), ""),
                "educationYears", row.get("education_years") == null ? 0 : row.get("education_years")));
            result.put("matchedOn", matchedOn);
            result.put("highlightFields", highlight(row, filters));
            result.put("score", filters.isEmpty() ? 1.0 : 1.0);
            return result;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", total == null ? 0 : total);
        result.put("records", records);
        return result;
    }

    private String filterClause(Filter filter, List<Object> args) {
        String comparison = comparison(filter.operator());
        return switch (filter.field()) {
            case "keyword", "subjectId" -> {
                String value = like(filter.value());
                args.addAll(List.of(value, value, value, value));
                yield "(s.subject_code LIKE ? OR s.last_name LIKE ? OR s.first_name LIKE ? OR s.remarks LIKE ?)";
            }
            case "diagnosis" -> {
                String value = like(filter.value());
                args.addAll(List.of(value, value, value));
                yield "EXISTS (SELECT 1 FROM diagnosis d WHERE d.subject_id=s.id AND " +
                    "(d.diagnosis_name_cn LIKE ? OR d.icd10_code LIKE ? OR d.icd10_name LIKE ?))";
            }
            case "sex" -> {
                String value = string(filter.value());
                args.add("M".equalsIgnoreCase(value) ? "1" : "F".equalsIgnoreCase(value) ? "2" : value);
                yield "sx.code " + comparison + " ?";
            }
            case "age" -> {
                args.add(number(filter.value()));
                yield "TIMESTAMPDIFF(YEAR,s.birth_date,CURDATE()) " + comparison + " ?";
            }
            case "educationYears" -> { args.add(number(filter.value())); yield "s.education_years " + comparison + " ?"; }
            case "cohort" -> {
                Object value = filter.operator().equals("contains") ? like(filter.value()) : filter.value();
                args.addAll(List.of(value, value));
                yield "EXISTS (SELECT 1 FROM subject_cohort sc JOIN cohort c ON c.id=sc.cohort_id " +
                    "WHERE sc.subject_id=s.id AND (c.code " + comparison + " ? OR c.name " + comparison + " ?))";
            }
            case "scale" -> {
                String value = filter.operator().equals("contains") ? like(filter.value()) : string(filter.value());
                args.addAll(List.of(value, value, value));
                yield "EXISTS (SELECT 1 FROM scale_assessment sa JOIN scale_instrument si ON si.id=sa.instrument_id " +
                    "WHERE sa.subject_id=s.id AND (si.code " + comparison + " ? OR si.name " + comparison +
                    " ? OR si.name_zh " + comparison + " ?))";
            }
            case "score" -> { args.add(number(filter.value())); yield "EXISTS (SELECT 1 FROM scale_assessment sa WHERE sa.subject_id=s.id AND sa.total_score " + comparison + " ?)"; }
            case "gene" -> {
                args.add(filter.operator().equals("contains") ? like(filter.value()) : string(filter.value()));
                yield "EXISTS (SELECT 1 FROM genetics_sample gs JOIN genetics_variant gv ON gv.sample_id=gs.id " +
                    "WHERE gs.subject_id=s.id AND gv.gene_symbol " + comparison + " ?)";
            }
            case "variantType" -> {
                args.add(filter.value());
                yield "EXISTS (SELECT 1 FROM genetics_sample gs JOIN genetics_variant gv ON gv.sample_id=gs.id " +
                    "WHERE gs.subject_id=s.id AND gv.variant_type " + comparison + " ?)";
            }
            case "modality" -> {
                String value = filter.operator().equals("contains") ? like(filter.value()) : string(filter.value());
                args.addAll(List.of(value, value));
                yield "EXISTS (SELECT 1 FROM imaging_session ims JOIN imaging_modality im ON im.id=ims.modality_id " +
                    "WHERE ims.subject_id=s.id AND (im.code " + comparison + " ? OR im.name " + comparison + " ?))";
            }
            case "labTestId" -> {
                args.add(longValue(filter.value()));
                yield "EXISTS (SELECT 1 FROM lab_result lr WHERE lr.subject_id=s.id AND lr.lab_test_item_id " + comparison + " ?)";
            }
            case "isAbnormal" -> {
                args.add(booleanValue(filter.value()));
                yield "EXISTS (SELECT 1 FROM lab_result lr WHERE lr.subject_id=s.id AND lr.is_abnormal " + comparison + " ?)";
            }
            default -> throw new IllegalArgumentException("unsupported field");
        };
    }

    private String scopeSql(List<Object> args) {
        var scope = scopeGuard.currentScope();
        if (scope.admin()) return "";
        List<String> clauses = new ArrayList<>();
        if (scope.institutionId() != null) {
            clauses.add("s.enrollment_institution_id=?");
            args.add(scope.institutionId());
        }
        if (!scope.projectIds().isEmpty()) {
            clauses.add("EXISTS (SELECT 1 FROM subject_cohort sc0 JOIN cohort c0 ON c0.id=sc0.cohort_id " +
                "WHERE sc0.subject_id=s.id AND c0.project_id IN (" + placeholders(scope.projectIds().size()) + "))");
            args.addAll(scope.projectIds());
        }
        if (clauses.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有数据范围");
        return " AND (" + String.join(" OR ", clauses) + ")";
    }

    @GetMapping("/saved")
    public ApiResponse<List<SavedSearch>> getSavedSearches() {
        return ApiResponse.ok(savedRepo.findByUserId(currentUserId()));
    }

    @PostMapping("/saved")
    public ApiResponse<SavedSearch> saveSearch(@RequestBody Map<String, Object> body) {
        String name = stringOrNull(body.get("name"));
        if (!hasText(name) || name.length() > 200) throw new IllegalArgumentException("检索名称无效");
        SavedSearch saved = new SavedSearch();
        saved.setName(name.trim());
        try { saved.setQueryJson(mapper.writeValueAsString(body.getOrDefault("query", Map.of()))); }
        catch (Exception e) { throw new IllegalArgumentException("检索条件格式无效", e); }
        saved.setUserId(currentUserId());
        return ApiResponse.created(savedRepo.save(saved));
    }

    @DeleteMapping("/saved/{id}")
    public ApiResponse<Void> deleteSavedSearch(@PathVariable Long id) {
        SavedSearch saved = savedRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("已保存检索不存在"));
        if (!Objects.equals(saved.getUserId(), currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除其他用户的检索");
        }
        savedRepo.delete(saved);
        return ApiResponse.ok(null);
    }

    private long currentUserId() {
        try { return Long.parseLong(request.getHeader("X-User-Id")); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }

    private static Map<String, String> highlight(Map<String, Object> row, List<Filter> filters) {
        Map<String, String> values = new LinkedHashMap<>();
        if (filters.stream().anyMatch(f -> Set.of("keyword", "subjectId").contains(f.field()))) {
            values.put("subjectCode", Objects.toString(row.get("subject_code"), ""));
        }
        return values;
    }
    private static String comparison(String operator) {
        return switch (operator) {
            case "eq" -> "="; case "ne" -> "<>"; case "gt" -> ">"; case "lt" -> "<";
            case "gte" -> ">="; case "lte" -> "<="; case "contains" -> "LIKE";
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }
    private static String like(Object value) { return "%" + string(value).trim() + "%"; }
    private static Number number(Object value) {
        if (value instanceof Number n) return n;
        try { return Double.valueOf(string(value)); } catch (Exception e) { throw new IllegalArgumentException("numeric value required"); }
    }
    private static Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(string(value)); } catch (Exception e) { throw new IllegalArgumentException("integer value required"); }
    }
    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(string(value));
    }
    private static String string(Object value) {
        if (value == null) throw new IllegalArgumentException("value is required");
        return value.toString();
    }
    private static String stringOrNull(Object value) { return value == null ? null : value.toString(); }
    private static int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String firstNonBlank(String a, String b) { return hasText(a) ? a : b; }
    private static void add(List<Filter> filters, String field, String operator, String value) {
        if (hasText(value)) filters.add(new Filter(field, operator, value));
    }
    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private record Filter(String field, String operator, Object value) {}
}
