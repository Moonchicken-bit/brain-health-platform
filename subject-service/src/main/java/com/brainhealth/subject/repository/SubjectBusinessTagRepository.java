package com.brainhealth.subject.repository;
import com.brainhealth.subject.entity.SubjectBusinessTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SubjectBusinessTagRepository extends JpaRepository<SubjectBusinessTag, Long> {
    List<SubjectBusinessTag> findByProjectIdAndIsActiveTrueOrderByName(Long projectId);
}
