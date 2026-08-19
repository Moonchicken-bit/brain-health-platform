package com.brainhealth.lab.service;

import com.brainhealth.lab.entity.*;
import com.brainhealth.lab.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LabReportParsingServiceTest {
    @TempDir Path temp;

    @Test
    void parsesChineseCsvAndMapsTestDictionary() throws Exception {
        Path csv = temp.resolve("检验结果.csv");
        Files.writeString(csv, "检验项目,结果,单位,参考范围,提示\n血糖,7.2,mmol/L,3.9-6.1,高\n",
            StandardCharsets.UTF_8);
        LabReportUpload upload = upload("u1", csv);
        LabReportUploadRepository uploads = mock(LabReportUploadRepository.class);
        LabTestItemRepository tests = mock(LabTestItemRepository.class);
        when(uploads.findById("u1")).thenReturn(Optional.of(upload));
        LabTestItem glucose = new LabTestItem();
        glucose.setId(9L); glucose.setName("血糖"); glucose.setUnit("mmol/L");
        when(tests.findAll()).thenReturn(List.of(glucose));

        var service = new LabReportParsingService(uploads, tests, mock(LabResultRepository.class));
        var preview = service.preview("u1");
        assertEquals(1, preview.candidates().size());
        assertEquals(9L, preview.candidates().get(0).labTestId());
        assertEquals("7.2", preview.candidates().get(0).value());
        assertEquals("PARSED", upload.getStatus());
    }

    @Test
    void rejectsArchiveTraversal() throws Exception {
        Path zip = temp.resolve("bad.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new java.util.zip.ZipEntry("../outside.csv"));
            output.write("项目,结果".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        LabReportUpload upload = upload("u2", zip);
        LabReportUploadRepository uploads = mock(LabReportUploadRepository.class);
        when(uploads.findById("u2")).thenReturn(Optional.of(upload));
        var service = new LabReportParsingService(uploads, mock(LabTestItemRepository.class),
            mock(LabResultRepository.class));
        assertThrows(IllegalArgumentException.class, () -> service.preview("u2"));
        assertEquals("PARSE_FAILED", upload.getStatus());
    }

    private static LabReportUpload upload(String id, Path path) throws Exception {
        LabReportUpload upload = new LabReportUpload();
        upload.setId(id);
        upload.setSubjectId(1L);
        upload.setSessionId(2L);
        upload.setOriginalName(path.getFileName().toString());
        upload.setStoragePath(path.toString());
        upload.setFileSize(Files.size(path));
        upload.setStatus("UPLOADED");
        return upload;
    }
}
