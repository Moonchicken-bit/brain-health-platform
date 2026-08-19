package com.brainhealth.scale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalFieldDictionaryServiceTest {
    @Test
    void loadsAllApprovedFieldsAndSupportsClinicalCategorySearch() {
        ClinicalFieldDictionaryService service = new ClinicalFieldDictionaryService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "dictionaryPath", "../../clinical_field_dictionary.json");
        service.load();

        assertEquals(10290, service.summary().get("fieldCount"));
        assertEquals(3416, service.search("认知评估", null, null, null, 1, 500).getTotal());
        assertTrue(service.search(null, "STROOP", "V1", null, 1, 20).getTotal() > 0);
        assertEquals(1, service.search(null, null, null, "SUBID", 1, 20).getTotal());
    }
}
