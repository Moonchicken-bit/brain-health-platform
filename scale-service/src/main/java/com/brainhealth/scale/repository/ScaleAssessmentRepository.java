package com.brainhealth.scale.repository;

import com.brainhealth.scale.entity.ScaleAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScaleAssessmentRepository extends JpaRepository<ScaleAssessment, Long> {
    List<ScaleAssessment> findBySubjectIdOrderByAssessmentDateDesc(Long subjectId);
    List<ScaleAssessment> findByInstrumentIdAndSubjectIdOrderByAssessmentDateDesc(Long instrumentId, Long subjectId);
    List<ScaleAssessment> findBySessionId(Long sessionId);
    long countByDataEntryStatus(String status);
}
