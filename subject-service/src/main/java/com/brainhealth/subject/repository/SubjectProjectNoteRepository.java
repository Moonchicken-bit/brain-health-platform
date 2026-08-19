package com.brainhealth.subject.repository;
import com.brainhealth.subject.entity.SubjectProjectNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SubjectProjectNoteRepository extends JpaRepository<SubjectProjectNote, Long> {
    List<SubjectProjectNote> findBySubjectIdOrderByRevisionNoDesc(Long subjectId);
    Optional<SubjectProjectNote> findFirstBySubjectIdOrderByRevisionNoDesc(Long subjectId);
}
