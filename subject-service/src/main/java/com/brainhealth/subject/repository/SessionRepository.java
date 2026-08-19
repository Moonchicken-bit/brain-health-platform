package com.brainhealth.subject.repository;

import com.brainhealth.subject.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findBySubjectIdAndIsActiveTrueOrderBySessionDateAsc(Long subjectId);

    List<Session> findBySubjectIdAndIsActiveTrueOrderBySessionDateDesc(Long subjectId);

    Optional<Session> findByIdAndIsActiveTrue(Long id);

    long countBySubjectId(Long subjectId);
}
