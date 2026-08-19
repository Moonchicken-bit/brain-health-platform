package com.brainhealth.scale.service;

import com.brainhealth.scale.entity.VisitAttachment;
import com.brainhealth.scale.repository.VisitAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitAttachmentServiceTest {
    @TempDir
    Path storageRoot;

    @Test
    void storesLoadsAndDeletesUsingGeneratedObjectKey() throws Exception {
        VisitAttachmentRepository repository = mock(VisitAttachmentRepository.class);
        AtomicReference<VisitAttachment> saved = new AtomicReference<>();
        when(repository.save(any(VisitAttachment.class))).thenAnswer(invocation -> {
            VisitAttachment attachment = invocation.getArgument(0);
            saved.set(attachment);
            return attachment;
        });
        when(repository.findById(any(String.class))).thenAnswer(
                invocation -> Optional.ofNullable(saved.get()));

        VisitAttachmentService service =
                new VisitAttachmentService(repository, storageRoot.toString(), 1024);
        MockMultipartFile file =
                new MockMultipartFile("file", "../report.txt", "text/plain", "result".getBytes());

        var dto = service.store(file, 10L, "SF1", "SF1_JCZB_REPORT");

        assertEquals("report.txt", dto.originalName());
        assertFalse(saved.get().getObjectKey().contains("report.txt"));
        assertArrayEquals("result".getBytes(), service.loadContent(dto.id()).getContentAsByteArray());

        service.delete(dto.id());
        assertThrows(IllegalArgumentException.class, () -> service.loadContent(dto.id()));
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        VisitAttachmentRepository repository = mock(VisitAttachmentRepository.class);
        VisitAttachmentService service =
                new VisitAttachmentService(repository, storageRoot.toString(), 3);

        assertThrows(IllegalArgumentException.class, () ->
                service.store(new MockMultipartFile("file", new byte[0]), 10L, "SF1", "FIELD"));
        assertThrows(IllegalArgumentException.class, () ->
                service.store(new MockMultipartFile("file", "1234".getBytes()), 10L, "SF1", "FIELD"));
    }
}
