package com.brainhealth.search.controller;

import com.brainhealth.common.security.DataScopeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchControllerTest {
    @Test
    void advancedSearchUsesAllCrossModalFiltersAndPagination() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        DataScopeGuard scope = new AdminScopeGuard();
        SearchController controller = new SearchController(
            jdbc, null, scope, null, new ObjectMapper());
        List<Map<String, Object>> filters = List.of(
            filter("age", "gte", 18), filter("sex", "eq", "M"),
            filter("educationYears", "gte", 9), filter("cohort", "eq", "CASE"),
            filter("scale", "contains", "MMSE"), filter("score", "gte", 20),
            filter("gene", "contains", "APOE"), filter("variantType", "eq", "SNV"),
            filter("modality", "eq", "MRI_T1"), filter("labTestId", "eq", 3),
            filter("isAbnormal", "eq", true));
        var response = controller.advancedSearch(Map.of(
            "filters", filters, "operator", "AND", "page", 3, "size", 12));
        assertEquals(3, response.getData().get("page"));
        assertEquals(12, response.getData().get("size"));
        assertTrue(jdbc.lastListSql.contains("scale_assessment"));
        assertTrue(jdbc.lastListSql.contains("genetics_variant"));
        assertTrue(jdbc.lastListSql.contains("imaging_modality"));
        assertTrue(jdbc.lastListSql.contains("lab_result"));
    }

    @Test
    void rejectsUnknownFieldsAndOperators() {
        SearchController controller = new SearchController(new CapturingJdbcTemplate(),
            null, new AdminScopeGuard(), null, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> controller.advancedSearch(
            Map.of("filters", List.of(filter("rawSql", "eq", "x")), "operator", "AND")));
    }

    private static Map<String, Object> filter(String field, String operator, Object value) {
        return Map.of("field", field, "operator", operator, "value", value);
    }

    private static class AdminScopeGuard extends DataScopeGuard {
        AdminScopeGuard() { super(null, null); }
        @Override public Scope currentScope() { return new Scope(true, null, Set.of()); }
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {
        String lastListSql;
        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastListSql = sql;
            return List.of();
        }
        @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(0L);
        }
    }
}
