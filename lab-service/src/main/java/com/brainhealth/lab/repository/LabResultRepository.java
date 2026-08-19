package com.brainhealth.lab.repository;
import com.brainhealth.lab.entity.LabResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, Long> {
    Page<LabResult> findBySessionId(Long sessionId, Pageable pageable);
    Page<LabResult> findBySubjectId(Long subjectId, Pageable pageable);
    Page<LabResult> findByLabTestId(Long labTestId, Pageable pageable);
    Page<LabResult> findByIsAbnormal(Boolean isAbnormal, Pageable pageable);
    Page<LabResult> findBySubjectIdIn(Collection<Long> subjectIds, Pageable pageable);
}
