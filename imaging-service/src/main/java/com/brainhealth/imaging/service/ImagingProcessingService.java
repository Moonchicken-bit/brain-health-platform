package com.brainhealth.imaging.service;

import com.brainhealth.imaging.entity.ImagingSeries;
import com.brainhealth.imaging.entity.ImagingSession;
import com.brainhealth.imaging.repository.ImagingSeriesRepository;
import com.brainhealth.imaging.repository.ImagingSessionRepository;
import com.brainhealth.imaging.repository.ImagingProcessingTaskRepository;
import com.brainhealth.imaging.entity.ImagingProcessingTask;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;

@Service
public class ImagingProcessingService {
    private static final String BUCKET = "brain-health-imaging";
    private static final Set<String> PIPELINES =
            Set.of("fmriprep", "mriqc", "qsiprep", "petprep", "aslprep");
    private final ImagingSessionRepository sessionRepository;
    private final ImagingSeriesRepository seriesRepository;
    private final ArchiveInspectionService archiveService;
    private final MinioClient minio;
    private final Path workRoot;
    private final String dcm2niixExecutable;
    private final Map<String, String> pipelineExecutables;
    private final ImagingProcessingTaskRepository taskRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "imaging-processing");
        thread.setDaemon(true);
        return thread;
    });

    public ImagingProcessingService(
            ImagingSessionRepository sessionRepository,
            ImagingSeriesRepository seriesRepository,
            ImagingProcessingTaskRepository taskRepository,
            ArchiveInspectionService archiveService,
            MinioClient minio,
            @Value("${brain-health.imaging.processing-root:${java.io.tmpdir}/brain-health/processing}") String root,
            @Value("${brain-health.imaging.dcm2niix-executable:dcm2niix}") String dcm2niixExecutable,
            @Value("${brain-health.imaging.pipeline.fmriprep:fmriprep}") String fmriprep,
            @Value("${brain-health.imaging.pipeline.mriqc:mriqc}") String mriqc,
            @Value("${brain-health.imaging.pipeline.qsiprep:qsiprep}") String qsiprep,
            @Value("${brain-health.imaging.pipeline.petprep:petprep}") String petprep,
            @Value("${brain-health.imaging.pipeline.aslprep:aslprep}") String aslprep) {
        this.sessionRepository = sessionRepository;
        this.seriesRepository = seriesRepository;
        this.taskRepository = taskRepository;
        this.archiveService = archiveService;
        this.minio = minio;
        this.workRoot = Path.of(root).toAbsolutePath().normalize();
        this.dcm2niixExecutable = dcm2niixExecutable;
        this.pipelineExecutables = Map.of(
                "fmriprep", fmriprep, "mriqc", mriqc, "qsiprep", qsiprep,
                "petprep", petprep, "aslprep", aslprep);
    }

    public ProcessingTask submitBids(Long imagingSessionId) {
        ImagingSession session = requireSession(imagingSessionId);
        String id = "bids-" + UUID.randomUUID();
        ProcessingTask task = new ProcessingTask(id, imagingSessionId, session.getSubjectId(),
                "BIDS", "QUEUED", 0, List.of("BIDS 转换任务已排队"), null, null, Instant.now());
        saveTask(task);
        if (taskRepository.claimQueued(id) == 1) executor.submit(() -> runBids(task));
        return task;
    }

    public ProcessingTask submitPreprocessing(Long imagingSessionId, String pipeline) {
        if (pipeline == null || !PIPELINES.contains(pipeline.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不支持的预处理流水线");
        }
        ImagingSession session = requireSession(imagingSessionId);
        String normalized = pipeline.toLowerCase(Locale.ROOT);
        String id = "preproc-" + UUID.randomUUID();
        ProcessingTask task = new ProcessingTask(id, imagingSessionId, session.getSubjectId(),
                normalized, "QUEUED", 0, List.of("预处理任务已排队"), null, null, Instant.now());
        saveTask(task);
        session.setPreprocessingStatus("QUEUED");
        sessionRepository.save(session);
        if (taskRepository.claimQueued(id) == 1) executor.submit(() -> runPipeline(task));
        return task;
    }

    public ProcessingTask get(String id) {
        ProcessingTask task = taskRepository.findById(id).map(this::fromEntity).orElse(null);
        if (task == null) throw new IllegalArgumentException("影像处理任务不存在或服务重启后已失效");
        return task;
    }

    @PostConstruct
    void recoverTasksAndCheckTools() {
        for (ImagingProcessingTask entity : taskRepository.findByStatusIn(List.of("RUNNING"))) {
            ProcessingTask interrupted = fromEntity(entity).with("FAILED", 100,
                append(fromEntity(entity).logs(), "服务重启导致任务中断，请重新提交"), null, "service restarted");
            saveTask(interrupted);
        }
        for (ImagingProcessingTask entity : taskRepository.findByStatusIn(List.of("QUEUED"))) {
            ProcessingTask queued = fromEntity(entity);
            if (taskRepository.claimQueued(queued.id()) == 1) {
                executor.submit(() -> {
                    if ("BIDS".equals(queued.kind())) runBids(queued);
                    else runPipeline(queued);
                });
            }
        }
    }

    public Map<String, Boolean> toolAvailability() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("dcm2niix", executableAvailable(dcm2niixExecutable));
        pipelineExecutables.forEach((name, executable) -> result.put(name, executableAvailable(executable)));
        return result;
    }

    private static boolean executableAvailable(String executable) {
        Path direct = Path.of(executable);
        if (direct.isAbsolute()) return Files.isRegularFile(direct) && Files.isExecutable(direct);
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String directory : path.split(java.io.File.pathSeparator)) {
            for (String suffix : System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ? List.of("", ".exe", ".bat", ".cmd") : List.of("")) {
                Path candidate = Path.of(directory, executable + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return true;
            }
        }
        return false;
    }

    public List<Map<String, String>> browseBids(Long subjectId, String sessionLabel) {
        String prefix = bidsPrefix(subjectId, sessionLabel);
        List<Map<String, String>> files = new ArrayList<>();
        try {
            for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                    .bucket(BUCKET).prefix(prefix).recursive(true).build())) {
                Item item = result.get();
                files.add(Map.of("name", item.objectName().substring(prefix.length()),
                        "objectName", item.objectName(), "size", Long.toString(item.size()),
                        "lastModified", item.lastModified().toString()));
            }
            return files;
        } catch (Exception e) {
            throw new IllegalStateException("读取 BIDS 输出失败：" + e.getMessage(), e);
        }
    }

    private void runBids(ProcessingTask task) {
        Path root = workRoot.resolve(task.id()).normalize();
        try {
            update(task, "RUNNING", 5, "正在下载原始影像");
            Files.createDirectories(root);
            Path dicomRoot = root.resolve("dicom");
            Path output = root.resolve("bids");
            Files.createDirectories(dicomRoot);
            Files.createDirectories(output);
            List<ImagingSeries> series = seriesRepository.findByImagingSessionId(task.imagingSessionId());
            List<String> objects = series.stream().map(ImagingSeries::getFilePath)
                    .filter(path -> path != null && !path.isBlank()).distinct().toList();
            if (objects.isEmpty()) throw new IllegalStateException("该检查没有可转换的原始影像文件");
            int index = 0;
            for (String object : objects) {
                Path downloaded = root.resolve("source-" + index + extension(object));
                downloadObject(object, downloaded);
                String lower = object.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".zip") || lower.endsWith(".rar")) {
                    archiveService.extract(downloaded, object, dicomRoot.resolve("archive-" + index));
                } else {
                    Files.copy(downloaded, dicomRoot.resolve("image-" + index + extension(object)),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                index++;
            }
            update(task, "RUNNING", 45, "正在运行 dcm2niix");
            runCommand(List.of(dcm2niixExecutable, "-b", "y", "-z", "y",
                    "-o", output.toString(), dicomRoot.toString()), root, task);
            if (regularFileCount(output) == 0) throw new IllegalStateException("dcm2niix 未生成任何 BIDS/NIfTI 文件");
            Files.writeString(output.resolve("dataset_description.json"),
                    "{\"Name\":\"Brain Health Platform export\",\"BIDSVersion\":\"1.9.0\",\"DatasetType\":\"raw\"}",
                    StandardCharsets.UTF_8);
            update(task, "RUNNING", 85, "正在保存 BIDS 输出");
            String prefix = bidsPrefix(task.subjectId(), task.imagingSessionId().toString());
            uploadTree(output, prefix);
            saveTask(task.with("COMPLETED", 100,
                    append(current(task).logs(), "BIDS 转换完成"), prefix, null));
        } catch (Exception e) {
            saveTask(task.with("FAILED", 100,
                    append(current(task).logs(), "失败：" + e.getMessage()), null, e.getMessage()));
        } finally {
            ArchiveAnalysisService.deleteTree(root);
        }
    }

    private void runPipeline(ProcessingTask task) {
        Path root = workRoot.resolve(task.id()).normalize();
        ImagingSession session = requireSession(task.imagingSessionId());
        try {
            String prefix = bidsPrefix(task.subjectId(), task.imagingSessionId().toString());
            Path input = root.resolve("bids");
            Path output = root.resolve("output");
            Files.createDirectories(input);
            Files.createDirectories(output);
            downloadPrefix(prefix, input);
            if (regularFileCount(input) == 0) {
                throw new IllegalStateException("尚无 BIDS 数据，请先执行 BIDS 转换");
            }
            update(task, "RUNNING", 20, "正在启动 " + task.kind());
            String executable = pipelineExecutables.get(task.kind());
            List<String> command = pipelineCommand(task.kind(), executable, input, output, task.subjectId());
            runCommand(command, root, task);
            if (regularFileCount(output) == 0) throw new IllegalStateException("流水线未生成输出文件");
            String outputPrefix = "preprocessing/" + task.kind() + "/" + task.id() + "/";
            uploadTree(output, outputPrefix);
            session.setPreprocessingStatus("COMPLETED");
            sessionRepository.save(session);
            saveTask(task.with("COMPLETED", 100,
                    append(current(task).logs(), "预处理完成"), outputPrefix, null));
        } catch (Exception e) {
            session.setPreprocessingStatus("FAILED");
            sessionRepository.save(session);
            saveTask(task.with("FAILED", 100,
                    append(current(task).logs(), "失败：" + e.getMessage()), null, e.getMessage()));
        } finally {
            ArchiveAnalysisService.deleteTree(root);
        }
    }

    private List<String> pipelineCommand(String pipeline, String executable, Path input, Path output, Long subjectId) {
        String label = subjectId.toString();
        if ("mriqc".equals(pipeline)) {
            return List.of(executable, input.toString(), output.toString(), "participant",
                    "--participant-label", label, "--no-sub");
        }
        return List.of(executable, input.toString(), output.toString(), "participant",
                "--participant-label", label, "--skip-bids-validation");
    }

    private void runCommand(List<String> command, Path directory, ProcessingTask task) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).start();
        } catch (Exception e) {
            throw new IllegalStateException("无法启动 " + command.get(0) +
                    "；请在服务器安装并配置该程序", e);
        }
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(24, TimeUnit.HOURS)) {
            process.destroyForcibly();
            throw new IllegalStateException("影像处理超过 24 小时，已终止");
        }
        List<String> logs = current(task).logs();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) logs = append(logs, line.length() > 500 ? line.substring(0, 500) : line);
            if (logs.size() >= 200) break;
        }
        saveTask(current(task).with("RUNNING", 75, logs, null, null));
        if (process.exitValue() != 0) throw new IllegalStateException(
                command.get(0) + " 退出码为 " + process.exitValue());
    }

    private void downloadObject(String objectName, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (InputStream input = minio.getObject(GetObjectArgs.builder()
                .bucket(BUCKET).object(objectName).build())) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadPrefix(String prefix, Path root) throws Exception {
        for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                .bucket(BUCKET).prefix(prefix).recursive(true).build())) {
            Item item = result.get();
            Path target = root.resolve(item.objectName().substring(prefix.length())).normalize();
            if (!target.startsWith(root)) throw new SecurityException("对象路径越界");
            downloadObject(item.objectName(), target);
        }
    }

    private void uploadTree(Path root, String prefix) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                minio.putObject(PutObjectArgs.builder().bucket(BUCKET).object(prefix + relative)
                        .stream(Files.newInputStream(file), Files.size(file), -1)
                        .contentType(contentType(relative)).build());
            }
        }
    }

    private void update(ProcessingTask task, String status, int progress, String log) {
        ProcessingTask current = current(task);
        saveTask(current.with(status, progress, append(current.logs(), log), null, null));
    }

    private ProcessingTask current(ProcessingTask fallback) {
        return taskRepository.findById(fallback.id()).map(this::fromEntity).orElse(fallback);
    }

    private void saveTask(ProcessingTask task) {
        ImagingProcessingTask entity = new ImagingProcessingTask();
        entity.setTaskId(task.id());
        entity.setImagingSessionId(task.imagingSessionId());
        entity.setSubjectId(task.subjectId());
        entity.setKind(task.kind());
        entity.setStatus(task.status());
        entity.setProgress(task.progress());
        entity.setLogs(String.join("\n", task.logs()));
        entity.setOutputPrefix(task.outputPrefix());
        entity.setError(task.error());
        entity.setUpdatedAt(task.updatedAt());
        taskRepository.save(entity);
    }

    private ProcessingTask fromEntity(ImagingProcessingTask entity) {
        List<String> logs = entity.getLogs() == null || entity.getLogs().isBlank()
            ? List.of() : List.of(entity.getLogs().split("\\R"));
        return new ProcessingTask(entity.getTaskId(), entity.getImagingSessionId(), entity.getSubjectId(),
            entity.getKind(), entity.getStatus(), entity.getProgress(), logs, entity.getOutputPrefix(),
            entity.getError(), entity.getUpdatedAt());
    }

    private ImagingSession requireSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("影像检查不存在"));
    }

    private long regularFileCount(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static String extension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".nii.gz")) return ".nii.gz";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".bin" : name.substring(dot);
    }

    private static String bidsPrefix(Long subjectId, String sessionLabel) {
        String safeSession = sessionLabel.replaceAll("[^A-Za-z0-9_-]", "_");
        return "bids/sub-" + subjectId + "/ses-" + safeSession + "/";
    }

    private static String contentType(String name) {
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".tsv")) return "text/tab-separated-values";
        if (name.endsWith(".gz")) return "application/gzip";
        return "application/octet-stream";
    }

    private static List<String> append(List<String> source, String value) {
        List<String> copy = new ArrayList<>(source);
        copy.add(value);
        return List.copyOf(copy);
    }

    public record ProcessingTask(String id, Long imagingSessionId, Long subjectId, String kind,
                                 String status, int progress, List<String> logs,
                                 String outputPrefix, String error, Instant updatedAt) {
        public String jobId() { return id; }
        private ProcessingTask with(String newStatus, int newProgress, List<String> newLogs,
                                    String newOutput, String newError) {
            return new ProcessingTask(id, imagingSessionId, subjectId, kind, newStatus,
                    newProgress, newLogs, newOutput, newError, Instant.now());
        }
    }
}
