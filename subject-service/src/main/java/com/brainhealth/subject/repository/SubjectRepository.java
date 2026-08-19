package com.brainhealth.subject.repository;

import com.brainhealth.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long>,
        JpaSpecificationExecutor<Subject> {

    /**
     * Find an active (non-deleted) subject by primary key.
     */
    Optional<Subject> findByIdAndIsActiveTrue(Long id);

    /**
     * Check whether a subject with the given business subject-id already exists.
     */
    boolean existsBySubjectId(String subjectId);

    /**
     * Find a subject by its business identifier.
     */
    Optional<Subject> findBySubjectId(String subjectId);

    /**
     * Count how many active subjects are in the system.
     */
    long countByIsActiveTrue();
}
