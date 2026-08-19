package com.brainhealth.scale.repository;

import com.brainhealth.scale.entity.ScaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScaleItemRepository extends JpaRepository<ScaleItem, Long> {
    List<ScaleItem> findByInstrumentIdOrderByItemIndex(Long instrumentId);
}
