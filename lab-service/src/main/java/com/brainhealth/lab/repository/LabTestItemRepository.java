package com.brainhealth.lab.repository;
import com.brainhealth.lab.entity.LabTestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LabTestItemRepository extends JpaRepository<LabTestItem, Long> {
    List<LabTestItem> findByCategory(String category);
    List<LabTestItem> findByNameContaining(String keyword);
}
