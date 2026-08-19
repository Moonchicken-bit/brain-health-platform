package com.brainhealth.subject.repository;
import com.brainhealth.subject.entity.SubjectFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SubjectFavoriteRepository extends JpaRepository<SubjectFavorite, Long> {
    Optional<SubjectFavorite> findByUserIdAndSubjectId(Long userId, Long subjectId);
    List<SubjectFavorite> findByUserId(Long userId);
}
