package com.brainhealth.lab.repository;

import com.brainhealth.lab.entity.LabReportUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabReportUploadRepository extends JpaRepository<LabReportUpload, String> {
    List<LabReportUpload> findBySubjectIdAndSessionIdOrderByCreatedAtDesc(Long subjectId, Long sessionId);
}
