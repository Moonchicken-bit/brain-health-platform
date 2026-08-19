package com.brainhealth.imaging.repository;

import com.brainhealth.imaging.entity.ImagingProcessingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ImagingProcessingTaskRepository extends JpaRepository<ImagingProcessingTask, String> {
    List<ImagingProcessingTask> findByStatusIn(List<String> statuses);
    @Modifying
    @Transactional
    @Query("update ImagingProcessingTask t set t.status='RUNNING',t.updatedAt=CURRENT_TIMESTAMP " +
        "where t.taskId=:taskId and t.status='QUEUED'")
    int claimQueued(String taskId);
}
