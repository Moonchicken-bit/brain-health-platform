package com.brainhealth.subject.repository;
import com.brainhealth.subject.entity.SubjectTagAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SubjectTagAssignmentRepository extends JpaRepository<SubjectTagAssignment, Long> {
    Optional<SubjectTagAssignment> findBySubjectIdAndTagId(Long subjectId, Long tagId);
    List<SubjectTagAssignment> findBySubjectId(Long subjectId);
}
