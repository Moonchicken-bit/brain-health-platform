package com.brainhealth.genetics.repository;
import com.brainhealth.genetics.entity.GeneticsSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;

@Repository
public interface GeneticsSampleRepository extends JpaRepository<GeneticsSample, Long> {
    Page<GeneticsSample> findBySubjectId(Long subjectId, Pageable pageable);
    Page<GeneticsSample> findBySampleType(String sampleType, Pageable pageable);
    Page<GeneticsSample> findByPlatform(String platform, Pageable pageable);
    Page<GeneticsSample> findByQcStatus(String qcStatus, Pageable pageable);
    Page<GeneticsSample> findBySubjectIdIn(Collection<Long> subjectIds, Pageable pageable);
}
