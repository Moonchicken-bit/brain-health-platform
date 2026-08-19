package com.brainhealth.scale.repository;

import com.brainhealth.scale.entity.ScaleInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScaleInstrumentRepository extends JpaRepository<ScaleInstrument, Long> {
    List<ScaleInstrument> findByIsActiveTrueOrderByName();
    List<ScaleInstrument> findByCategoryAndIsActiveTrue(String category);
    List<ScaleInstrument> findByNameContainingAndIsActiveTrue(String keyword);
}
