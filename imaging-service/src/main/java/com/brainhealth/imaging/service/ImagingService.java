package com.brainhealth.imaging.service;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.imaging.entity.*;
import com.brainhealth.imaging.repository.*;
import com.brainhealth.imaging.dto.ArchiveImportRequest;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ImagingService {
    private final ImagingSessionRepository sessionRepo;
    private final ImagingSeriesRepository seriesRepo;

    public ImagingService(ImagingSessionRepository sessionRepo, ImagingSeriesRepository seriesRepo) {
        this.sessionRepo = sessionRepo;
        this.seriesRepo = seriesRepo;
    }

    public PageResult<ImagingSession> listSessions(Long subjectId, Long modalityId, String qcStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<ImagingSession> result;
        if (subjectId != null) result = sessionRepo.findBySubjectId(subjectId, pageable);
        else if (modalityId != null) result = sessionRepo.findByModalityId(modalityId, pageable);
        else if (qcStatus != null) result = sessionRepo.findByQcStatus(qcStatus, pageable);
        else result = sessionRepo.findAll(pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public PageResult<ImagingSession> listSessionsForSubjects(Collection<Long> subjectIds, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<ImagingSession> result = subjectIds.isEmpty()
            ? Page.empty(pageable) : sessionRepo.findBySubjectIdIn(subjectIds, pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public ImagingSession getSession(Long id) {
        return sessionRepo.findById(id).orElse(null);
    }

    @Transactional
    public ImagingSession createSession(ImagingSession session) {
        return sessionRepo.save(session);
    }

    public List<ImagingSeries> getSeries(Long sessionId) {
        return seriesRepo.findByImagingSessionId(sessionId);
    }

    public ImagingSeries getSeriesDetail(Long id) {
        return seriesRepo.findById(id).orElse(null);
    }

    @Transactional
    public ImagingSeries createSeries(ImagingSeries series) {
        return seriesRepo.save(series);
    }

    @Transactional
    public ImagingSeries importDirectDicom(Long clinicalSessionId, Long subjectId, Long modalityId,
                                            String objectName,
                                            DicomMetadataService.DicomMetadata metadata) {
        if (clinicalSessionId == null || subjectId == null || modalityId == null) {
            throw new IllegalArgumentException("直接导入 DICOM 时访视、受试者和影像模态不能为空");
        }
        ImagingSession session = sessionRepo.findBySessionId(clinicalSessionId).orElseGet(ImagingSession::new);
        if (session.getId() == null) {
            session.setSessionId(clinicalSessionId);
            session.setSubjectId(subjectId);
            session.setModalityId(modalityId);
            session.setQcStatus("Pending");
            session.setPreprocessingStatus("None");
            session = sessionRepo.save(session);
        } else if (!Objects.equals(session.getSubjectId(), subjectId)) {
            throw new IllegalArgumentException("访视已经关联到其他受试者");
        }
        ImagingSeries series = new ImagingSeries();
        series.setImagingSessionId(session.getId());
        series.setSeriesUid(metadata.seriesInstanceUid() == null || metadata.seriesInstanceUid().isBlank()
                ? "uploaded-" + UUID.randomUUID() : metadata.seriesInstanceUid());
        series.setSeriesNumber(metadata.seriesNumber() == null
                ? seriesRepo.findByImagingSessionId(session.getId()).size() + 1 : metadata.seriesNumber());
        series.setSeriesDescription(metadata.seriesDescription());
        series.setSequenceName(metadata.modality());
        series.setNumberOfFiles(1);
        series.setFileType("DICOM");
        series.setFilePath(objectName);
        series.setQcStatus("Pending");
        ImagingSeries saved = seriesRepo.save(series);
        session.setSeriesCount(seriesRepo.findByImagingSessionId(session.getId()).size());
        sessionRepo.save(session);
        return saved;
    }

    @Transactional
    public ImagingSession confirmArchiveImport(ArchiveImportRequest request) {
        if (request.getSubjectId() == null || request.getSessionId() == null || request.getModalityId() == null) {
            throw new IllegalArgumentException("受试者、访视和影像模态不能为空");
        }
        if (request.getSeries() == null || request.getSeries().isEmpty()) {
            throw new IllegalArgumentException("没有可导入的影像序列");
        }
        ImagingSession session = sessionRepo.findBySessionId(request.getSessionId()).orElseGet(ImagingSession::new);
        if (session.getId() == null) {
            session.setSessionId(request.getSessionId());
            session.setSubjectId(request.getSubjectId());
            session.setModalityId(request.getModalityId());
            session.setAcquisitionDate(request.getAcquisitionDate());
            session.setQcStatus("Pending");
            session.setPreprocessingStatus("None");
            session = sessionRepo.save(session);
        } else if (!Objects.equals(session.getSubjectId(), request.getSubjectId())) {
            throw new IllegalArgumentException("访视已关联到其他受试者");
        }
        if (!seriesRepo.findByImagingSessionId(session.getId()).isEmpty()) {
            throw new IllegalArgumentException("该访视已导入影像序列，请勿重复确认");
        }
        int fallbackNumber = 1;
        for (ArchiveImportRequest.Series source : request.getSeries()) {
            ImagingSeries series = new ImagingSeries();
            series.setImagingSessionId(session.getId());
            series.setSeriesUid(source.getSeriesInstanceUid());
            series.setSeriesNumber(source.getSeriesNumber() == null ? fallbackNumber : source.getSeriesNumber());
            series.setSeriesDescription(source.getDescription());
            series.setSequenceName(source.getModality());
            series.setNumberOfFiles(source.getFileCount() == null ? 0 : source.getFileCount());
            series.setFileType("DICOM_ARCHIVE");
            series.setFilePath(request.getSourceObject());
            series.setQcStatus("Pending");
            seriesRepo.save(series);
            fallbackNumber++;
        }
        session.setSeriesCount(request.getSeries().size());
        return sessionRepo.save(session);
    }

    @Transactional
    public ImagingSeries updateQC(Long seriesId, String qcStatus, String qcNotes) {
        ImagingSeries series = seriesRepo.findById(seriesId).orElseThrow(() -> new RuntimeException("Series not found"));
        series.setQcStatus(qcStatus);
        if (qcNotes != null) series.setQcNotes(qcNotes);
        return seriesRepo.save(series);
    }
}
