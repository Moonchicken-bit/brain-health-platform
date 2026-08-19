package com.brainhealth.scale.repository;

import com.brainhealth.scale.entity.ScaleData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScaleDataRepository extends JpaRepository<ScaleData, Long> {
    List<ScaleData> findByAssessmentIdOrderByItemNumber(Long assessmentId);
    void deleteByAssessmentId(Long assessmentId);
}
