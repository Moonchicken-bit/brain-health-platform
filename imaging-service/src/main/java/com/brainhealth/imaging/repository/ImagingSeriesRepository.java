package com.brainhealth.imaging.repository;
import com.brainhealth.imaging.entity.ImagingSeries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImagingSeriesRepository extends JpaRepository<ImagingSeries, Long> {
    List<ImagingSeries> findByImagingSessionId(Long imagingSessionId);
}
