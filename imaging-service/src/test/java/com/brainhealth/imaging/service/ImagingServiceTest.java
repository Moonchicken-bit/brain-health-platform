package com.brainhealth.imaging.service;

import com.brainhealth.imaging.dto.ArchiveImportRequest;
import com.brainhealth.imaging.entity.ImagingSession;
import com.brainhealth.imaging.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImagingServiceTest {
    @Test
    void confirmsArchiveIntoSessionAndSeries() {
        ImagingSessionRepository sessions = mock(ImagingSessionRepository.class);
        ImagingSeriesRepository series = mock(ImagingSeriesRepository.class);
        when(sessions.findBySessionId(22L)).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(invocation -> {
            ImagingSession value = invocation.getArgument(0);
            if (value.getId() == null) {
                try {
                    var field = ImagingSession.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(value, 99L);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
            return value;
        });
        when(series.findByImagingSessionId(99L)).thenReturn(List.of());

        ArchiveImportRequest request = new ArchiveImportRequest();
        request.setSubjectId(11L);
        request.setSessionId(22L);
        request.setModalityId(1L);
        ArchiveImportRequest.Series item = new ArchiveImportRequest.Series();
        item.setSeriesInstanceUid("1.2.3");
        item.setSeriesNumber(3);
        item.setDescription("T1 MPRAGE");
        item.setFileCount(120);
        request.setSeries(List.of(item));

        ImagingSession result = new ImagingService(sessions, series).confirmArchiveImport(request);
        assertEquals(99L, result.getId());
        assertEquals(1, result.getSeriesCount());
        verify(series).save(argThat(value -> value.getNumberOfFiles() == 120
            && "DICOM_ARCHIVE".equals(value.getFileType())));
    }
}
