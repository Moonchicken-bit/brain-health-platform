package com.brainhealth.imaging.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ArchiveAnalysisTaskService {
    private final ArchiveAnalysisService analysisService;
    private final Path taskRoot;
    private final Map<String, TaskSnapshot> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "archive-analysis");
        thread.setDaemon(true);
        return thread;
    });

    public ArchiveAnalysisTaskService(
            ArchiveAnalysisService analysisService,
            @Value("${brain-health.imaging.analysis-root:${java.io.tmpdir}/brain-health/archive-analysis}") String root) {
        this.analysisService = analysisService;
        this.taskRoot = Paths.get(root).toAbsolutePath().normalize();
    }

    public String submit(Path source, String fileName, String objectName) throws IOException {
        Files.createDirectories(taskRoot);
        String taskId = UUID.randomUUID().toString();
        Path taskDir = taskRoot.resolve(taskId);
        Files.createDirectories(taskDir);
        Path archive = taskDir.resolve("source.archive");
        Files.copy(source, archive, StandardCopyOption.REPLACE_EXISTING);
        tasks.put(taskId, new TaskSnapshot(taskId, "QUEUED", 0, objectName, null, null, Instant.now()));
        executor.submit(() -> run(taskId, archive, fileName, objectName, taskDir.resolve("files")));
        return taskId;
    }

    public TaskSnapshot get(String taskId) {
        TaskSnapshot task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("解析任务不存在或已过期");
        return task;
    }

    private void run(String taskId, Path archive, String fileName, String objectName, Path extracted) {
        tasks.put(taskId, new TaskSnapshot(taskId, "ANALYZING", 10, objectName, null, null, Instant.now()));
        try {
            var result = analysisService.analyze(archive, fileName, extracted);
            tasks.put(taskId, new TaskSnapshot(taskId, "COMPLETED", 100, objectName, result, null, Instant.now()));
        } catch (Exception error) {
            tasks.put(taskId, new TaskSnapshot(taskId, "FAILED", 100, objectName, null,
                error.getMessage(), Instant.now()));
        } finally {
            ArchiveAnalysisService.deleteTree(archive.getParent());
        }
    }

    public record TaskSnapshot(String taskId, String status, int progress, String objectName,
                               ArchiveAnalysisService.ArchiveAnalysis result, String error,
                               Instant updatedAt) { }
}
