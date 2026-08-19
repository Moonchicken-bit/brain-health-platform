package com.brainhealth.exportz.repository;
import com.brainhealth.exportz.entity.ExportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportRequestRepository extends JpaRepository<ExportRequest, Long> {
    Page<ExportRequest> findByStatus(String status, Pageable pageable);
    Page<ExportRequest> findByProjectId(Long projectId, Pageable pageable);
    Page<ExportRequest> findByExportType(String exportType, Pageable pageable);
    Page<ExportRequest> findByRequesterId(Long requesterId, Pageable pageable);
}
