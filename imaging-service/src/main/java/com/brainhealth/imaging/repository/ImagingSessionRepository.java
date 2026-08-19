package com.brainhealth.imaging.repository;
import com.brainhealth.imaging.entity.ImagingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface ImagingSessionRepository extends JpaRepository<ImagingSession, Long> {
    Page<ImagingSession> findBySubjectId(Long subjectId, Pageable pageable);
    Page<ImagingSession> findByModalityId(Long modalityId, Pageable pageable);
    Page<ImagingSession> findByQcStatus(String qcStatus, Pageable pageable);
    Page<ImagingSession> findBySubjectIdIn(Collection<Long> subjectIds, Pageable pageable);
    Optional<ImagingSession> findBySessionId(Long sessionId);
}
