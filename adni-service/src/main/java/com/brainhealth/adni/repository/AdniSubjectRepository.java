package com.brainhealth.adni.repository;
import com.brainhealth.adni.entity.AdniSubject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AdniSubjectRepository extends JpaRepository<AdniSubject, Long> {
    Page<AdniSubject> findByDiagnosis(String diagnosis, Pageable pageable);
    Page<AdniSubject> findBySex(String sex, Pageable pageable);
    Page<AdniSubject> findByApoeGenotype(String apoeGenotype, Pageable pageable);
    Page<AdniSubject> findByAgeBetween(Integer ageMin, Integer ageMax, Pageable pageable);
    Optional<AdniSubject> findByAdniSubjectId(String adniSubjectId);
}
