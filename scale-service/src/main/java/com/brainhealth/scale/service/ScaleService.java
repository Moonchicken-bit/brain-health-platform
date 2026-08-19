package com.brainhealth.scale.service;

import com.brainhealth.common.exception.BusinessException;
import com.brainhealth.common.exception.ErrorCode;
import com.brainhealth.scale.dto.*;
import com.brainhealth.scale.entity.*;
import com.brainhealth.scale.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class ScaleService {

    private static final Logger log = Logger.getLogger(ScaleService.class.getName());
    private final ScaleInstrumentRepository instrumentRepo;
    private final ScaleItemRepository itemRepo;
    private final ScaleAssessmentRepository assessmentRepo;
    private final ScaleDataRepository scaleDataRepo;
    private final JdbcTemplate jdbcTemplate;

    public void recordAudit(Long userId, String operationType, String targetType,
                            Long targetId, String detail) {
        jdbcTemplate.update("""
            INSERT INTO audit_log(user_id,created_at,updated_at,operation_type,operation_detail,
                                  target_id,target_type,operation_result)
            VALUES (?,NOW(6),NOW(6),?,?,?,?, 'SUCCESS')
            """, userId, operationType, detail, targetId, targetType);
    }
    private final ObjectMapper objectMapper;

    @Value("${scale.metadata.path:scale_metadata.json}")
    private String metadataPath;

    private JsonNode metadataRoot;

    public ScaleService(ScaleInstrumentRepository instrumentRepo,
                        ScaleItemRepository itemRepo,
                        ScaleAssessmentRepository assessmentRepo,
                        ScaleDataRepository scaleDataRepo,
                        JdbcTemplate jdbcTemplate,
                        ObjectMapper objectMapper) {
        this.instrumentRepo = instrumentRepo;
        this.itemRepo = itemRepo;
        this.assessmentRepo = assessmentRepo;
        this.scaleDataRepo = scaleDataRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadMetadata() {
        // Try multiple paths to find metadata
        String[] paths = { metadataPath, "scale_metadata.json", "../scale_metadata.json", "../../scale_metadata.json" };
        for (String p : paths) {
            try {
                File file = new File(p);
                if (file.exists()) {
                    metadataRoot = objectMapper.readTree(file);
                    log.info("Loaded scale metadata from " + file.getAbsolutePath());
                    return;
                }
            } catch (IOException e) { /* try next */ }
        }
        log.warning("Scale metadata file not found. Paths tried: " + String.join(", ", paths));
        metadataRoot = null;
    }

    public List<InstrumentDTO> listInstruments(String category, String keyword) {
        List<ScaleInstrument> instruments;
        if (keyword != null && !keyword.isEmpty()) {
            instruments = instrumentRepo.findByNameContainingAndIsActiveTrue(keyword);
        } else if (category != null && !category.isEmpty()) {
            instruments = instrumentRepo.findByCategoryAndIsActiveTrue(category);
        } else {
            instruments = instrumentRepo.findByIsActiveTrueOrderByName();
        }
        return instruments.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public InstrumentDetailDTO getInstrument(Long id) {
        ScaleInstrument inst = instrumentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCALE_NOT_FOUND));
        List<ScaleItem> items = itemRepo.findByInstrumentIdOrderByItemIndex(id);
        InstrumentDetailDTO dto = new InstrumentDetailDTO();
        dto.setInstrument(toDTO(inst));
        dto.setItems(items.stream().map(this::toItemDTO).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public AssessmentDTO createAssessment(AssessmentCreateRequest req) {
        ScaleAssessment a = new ScaleAssessment();
        a.setInstrumentId(req.getInstrumentId());
        a.setSubjectId(req.getSubjectId());
        a.setSessionId(req.getSessionId());
        a.setExaminerId(req.getExaminerId());
        a.setAssessmentDate(req.getAssessmentDate());
        a.setAdministrationMode(req.getAdministrationMode());
        a.setNotes(req.getNotes());
        a.setDataEntryStatus("Incomplete");
        a.setTotalScore(0.0);
        a = assessmentRepo.save(a);
        log.info("Created assessment id=" + a.getId() + " for subject " + req.getSubjectId());
        return toAssessmentDTO(a);
    }

    public AssessmentDTO getAssessment(Long id) {
        return toAssessmentDTO(assessmentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND)));
    }

    @Transactional
    public List<ScaleData> saveAssessmentItems(Long assessmentId, AssessmentItemRequest req) {
        ScaleAssessment assessment = assessmentRepo.findById(assessmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND));
        if ("Complete".equalsIgnoreCase(assessment.getDataEntryStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已提交的评估不能修改");
        }
        List<AssessmentItemRequest.Item> requested = req == null || req.getItems() == null
            ? List.of() : req.getItems();
        Set<Integer> indexes = new HashSet<>();
        List<ScaleData> records = new ArrayList<>();
        for (AssessmentItemRequest.Item item : requested) {
            if (item.getItemIndex() == null || item.getItemIndex() < 1 || !indexes.add(item.getItemIndex())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "量表项目序号无效或重复");
            }
            ScaleData data = new ScaleData();
            data.setAssessmentId(assessmentId);
            data.setInstrumentId(assessment.getInstrumentId());
            data.setItemNumber(item.getItemIndex());
            data.setItemTextSnapshot(item.getQuestionText());
            data.setResponseValue(item.getResponse() == null ? null : String.valueOf(item.getResponse()));
            data.setItemScore(item.getScore() == null ? 0.0 : item.getScore());
            records.add(data);
        }
        scaleDataRepo.deleteByAssessmentId(assessmentId);
        return scaleDataRepo.saveAll(records);
    }

    public List<ScaleData> getAssessmentItems(Long assessmentId) {
        if (!assessmentRepo.existsById(assessmentId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND);
        }
        return scaleDataRepo.findByAssessmentIdOrderByItemNumber(assessmentId);
    }

    @Transactional
    public AssessmentDTO submitAssessment(Long id, AssessmentSubmitRequest req) {
        ScaleAssessment a = assessmentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND));
        double total = 0;
        if (req.getScores() != null) {
            total = req.getScores().stream().mapToDouble(s -> s.getScore()).sum();
        } else {
            total = scaleDataRepo.findByAssessmentIdOrderByItemNumber(id).stream()
                .map(ScaleData::getItemScore).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        }
        a.setTotalScore(total);
        a.setDataEntryStatus("Complete");
        assessmentRepo.save(a);
        return toAssessmentDTO(a);
    }

    public List<AssessmentDTO> getSubjectAssessments(Long subjectId) {
        return assessmentRepo.findBySubjectIdOrderByAssessmentDateDesc(subjectId)
                .stream().map(this::toAssessmentDTO).collect(Collectors.toList());
    }

    public List<AssessmentDTO> getSessionAssessments(Long sessionId) {
        return assessmentRepo.findBySessionId(sessionId)
                .stream().map(this::toAssessmentDTO).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // New endpoints
    // ---------------------------------------------------------------

    /**
     * GET /api/v1/scales/visit-form/{visitCode}
     * Returns the complete CRF form for a visit, grouped by scale instrument.
     * Reads from the Python-generated scale_metadata.json.
     */
    public VisitFormResponse getVisitForm(String visitCode) {
        if (metadataRoot == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Scale metadata not loaded. Ensure scale_metadata.json exists at " + metadataPath);
        }

        JsonNode visits = metadataRoot.get("visits");
        if (visits == null || !visits.isArray()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid metadata structure: missing visits array");
        }

        JsonNode targetVisit = null;
        for (JsonNode v : visits) {
            if (visitCode.equals(v.get("code").asText())) {
                targetVisit = v;
                break;
            }
        }
        if (targetVisit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Visit not found: " + visitCode);
        }

        VisitFormResponse response = new VisitFormResponse();
        response.setVisitCode(targetVisit.get("code").asText());
        response.setVisitName(targetVisit.get("name").asText());

        // Index instruments by code for quick lookup
        JsonNode instruments = metadataRoot.get("instruments");
        Map<String, JsonNode> instrumentMap = new HashMap<>();
        if (instruments != null && instruments.isArray()) {
            for (JsonNode inst : instruments) {
                instrumentMap.put(inst.get("code").asText(), inst);
            }
        }

        List<ScaleFormDTO> scaleForms = new ArrayList<>();
        JsonNode scalesNode = targetVisit.get("scales");
        if (scalesNode != null && scalesNode.isArray()) {
            for (JsonNode scale : scalesNode) {
                String scaleCode = scale.get("code").asText();
                ScaleFormDTO form = new ScaleFormDTO();
                form.setCode(scaleCode);
                form.setName(scale.get("name").asText());

                JsonNode instDef = instrumentMap.get(scaleCode);
                if (instDef != null) {
                    form.setMaxScore(instDef.has("max_score") && !instDef.get("max_score").isNull()
                            ? instDef.get("max_score").asDouble() : null);
                    form.setCutoff(instDef.has("cutoff") && !instDef.get("cutoff").isNull()
                            ? instDef.get("cutoff").asDouble() : null);

                    // Don't load items in the overview — they load on-demand per scale.
                    // Just set empty items list; items are served by getVisitScaleItems().
                    form.setItems(new ArrayList<>());
                } else {
                    form.setItems(new ArrayList<>());
                }
                JsonNode itemsNode = scale.get("items");
                if (itemsNode != null && itemsNode.isArray()) {
                    int count = 0;
                    for (JsonNode item : itemsNode) {
                        String itemCode = item.has("code") ? item.get("code").asText() : "";
                        if (belongsToVisit(itemCode, visitCode)) count++;
                    }
                    form.setItemCount(count);
                }
                scaleForms.add(form);
            }
        }
        Integer customCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM visit_form_custom_field WHERE visit_code=? AND status='PUBLISHED'",
            Integer.class, visitCode);
        if (customCount != null && customCount > 0) {
            ScaleFormDTO custom = new ScaleFormDTO();
            custom.setCode("CUSTOM");
            custom.setName("\u7ba1\u7406\u5458\u65b0\u589e\u9879\u76ee");
            custom.setItems(new ArrayList<>());
            custom.setItemCount(customCount);
            scaleForms.add(custom);
        }
        response.setScales(scaleForms);
        return response;
    }

    /**
     * GET /api/v1/scales/visit-form/{visitCode}/scale/{scaleCode}
     * Returns items for a single scale — lazy loading to avoid transferring 6000+ items at once.
     */
    public ScaleFormDTO getVisitScaleItems(String visitCode, String scaleCode) {
        if ("CUSTOM".equalsIgnoreCase(scaleCode)) {
            ScaleFormDTO form = new ScaleFormDTO();
            form.setCode("CUSTOM");
            form.setName("\u7ba1\u7406\u5458\u65b0\u589e\u9879\u76ee");
            List<FormItemDTO> items = new ArrayList<>();
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                    "SELECT field_code,label,field_type,unit,options_json,required_flag " +
                    "FROM visit_form_custom_field WHERE visit_code=? AND status='PUBLISHED' " +
                    "ORDER BY sort_order,id", visitCode)) {
                FormItemDTO item = new FormItemDTO();
                item.setCode(visitCode + "_CUSTOM_" + row.get("field_code"));
                item.setName(String.valueOf(row.get("label")));
                item.setType(String.valueOf(row.get("field_type")));
                item.setUnit(row.get("unit") == null ? "" : String.valueOf(row.get("unit")));
                Object required = row.get("required_flag");
                item.setRequired(required instanceof Boolean value ? value
                    : required instanceof Number number && number.intValue() != 0);
                List<OptionDTO> options = new ArrayList<>();
                Object rawOptions = row.get("options_json");
                if (rawOptions != null) {
                    try {
                        JsonNode optionArray = objectMapper.readTree(String.valueOf(rawOptions));
                        for (JsonNode option : optionArray) {
                            OptionDTO dto = new OptionDTO();
                            dto.setCode(option.asText());
                            dto.setLabel(option.asText());
                            dto.setScore(0);
                            options.add(dto);
                        }
                    } catch (Exception ignored) { }
                }
                item.setOptions(options);
                items.add(item);
            }
            form.setItems(items);
            form.setItemCount(items.size());
            return form;
        }
        if (metadataRoot == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Metadata not loaded");
        }
        JsonNode visits = metadataRoot.get("visits");
        for (JsonNode v : visits) {
            if (visitCode.equals(v.get("code").asText())) {
                JsonNode scales = v.get("scales");
                if (scales != null && scales.isArray()) {
                    for (JsonNode scale : scales) {
                        if (scaleCode.equals(scale.get("code").asText())) {
                            ScaleFormDTO form = new ScaleFormDTO();
                            form.setCode(scaleCode);
                            form.setName(scale.get("name").asText());
                            form.setMaxScore(scale.has("max_score") && !scale.get("max_score").isNull()
                                    ? scale.get("max_score").asDouble() : null);
                            form.setCutoff(scale.has("cutoff") && !scale.get("cutoff").isNull()
                                    ? scale.get("cutoff").asDouble() : null);

                            List<FormItemDTO> items = new ArrayList<>();
                            Map<String, Map<String, Object>> overrides = itemOverrides(visitCode, scaleCode);
                            JsonNode itemsNode = scale.get("items");
                            if (itemsNode != null && itemsNode.isArray()) {
                                for (JsonNode itemNode : itemsNode) {
                                    String itemCode = itemNode.has("code") ? itemNode.get("code").asText() : "";
                                    if (!belongsToVisit(itemCode, visitCode)) continue;
                                    Map<String, Object> override = overrides.get(itemCode);
                                    if (override != null && "DISABLED".equalsIgnoreCase(
                                            Objects.toString(override.get("status"), ""))) continue;
                                    FormItemDTO item = new FormItemDTO();
                                    item.setCode(itemCode);
                                    String originalName = itemNode.has("name") ? itemNode.get("name").asText() : "";
                                    item.setName(override != null && override.get("label_override") != null
                                        ? String.valueOf(override.get("label_override")) : originalName);
                                    item.setType(itemNode.has("type") ? itemNode.get("type").asText() : "text");
                                    boolean originalRequired = itemNode.has("required") && !itemNode.get("required").isNull()
                                        && itemNode.get("required").asBoolean();
                                    Object requiredOverride = override == null ? null : override.get("required_override");
                                    item.setRequired(requiredOverride instanceof Number number
                                        ? number.intValue() != 0 : originalRequired);
                                    item.setUnit(itemNode.has("unit") ? itemNode.get("unit").asText() : "");
                                    List<OptionDTO> opts = new ArrayList<>();
                                    JsonNode optsNode = itemNode.get("options");
                                    if (optsNode != null && optsNode.isArray()) {
                                        for (JsonNode o : optsNode) {
                                            OptionDTO opt = new OptionDTO();
                                            opt.setCode(o.has("code") ? o.get("code").asText() : "");
                                            opt.setLabel(o.has("label") ? o.get("label").asText() : "");
                                            opt.setScore(o.has("score") ? o.get("score").asDouble() : 0.0);
                                            opts.add(opt);
                                        }
                                    }
                                    item.setOptions(opts);
                                    items.add(item);
                                }
                            }
                            form.setItems(items);
                            form.setItemCount(items.size());
                            return form;
                        }
                    }
                }
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "Scale not found: " + visitCode + "/" + scaleCode);
    }

    private Map<String, Map<String, Object>> itemOverrides(String visitCode, String scaleCode) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT item_code,label_override,required_override,status FROM visit_form_item_override " +
                "WHERE visit_code=? AND scale_code=? ORDER BY version", visitCode, scaleCode)) {
            result.put(String.valueOf(row.get("item_code")), row);
        }
        return result;
    }

    public List<Map<String, Object>> listItemOverrides(String visitCode) {
        return jdbcTemplate.queryForList(
            "SELECT id,visit_code AS visitCode,scale_code AS scaleCode,item_code AS itemCode," +
            "label_override AS labelOverride,required_override AS requiredOverride,status,version " +
            "FROM visit_form_item_override WHERE visit_code=? ORDER BY scale_code,item_code,version",
            visitCode);
    }

    @Transactional
    public void updateItemOverride(String visitCode, String scaleCode, String itemCode,
                                   String label, Boolean required, String status, Long userId) {
        String normalizedStatus = status == null ? "PUBLISHED" : status.toUpperCase(Locale.ROOT);
        if (!Set.of("PUBLISHED", "DISABLED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("题目状态无效");
        }
        jdbcTemplate.update("""
            INSERT INTO visit_form_item_override
                (visit_code,scale_code,item_code,label_override,required_override,status,updated_by)
            VALUES (?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE label_override=VALUES(label_override),
                required_override=VALUES(required_override),status=VALUES(status),
                updated_by=VALUES(updated_by),updated_at=NOW()
            """, visitCode, scaleCode, itemCode,
            label == null || label.isBlank() ? null : label.trim(), required, normalizedStatus, userId);
    }

    private static boolean belongsToVisit(String itemCode, String visitCode) {
        if (itemCode == null || itemCode.isBlank()) return true;
        var matcher = java.util.regex.Pattern.compile("^(V\\d+|SF\\d+)_", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(itemCode);
        return !matcher.find() || matcher.group(1).equalsIgnoreCase(visitCode);
    }

    /**
     * POST /api/v1/scales/compute
     * Computes scale scores from user responses using the metadata JSON.
     * Falls back to summing numeric response values if metadata is unavailable.
     */
    public ComputeResponse computeScore(ComputeRequest req) {
        if (req.getInstrument() == null || req.getInstrument().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Instrument code is required");
        }

        if (metadataRoot == null) {
            return computeFromResponsesOnly(req);
        }

        JsonNode instruments = metadataRoot.get("instruments");
        if (instruments == null || !instruments.isArray()) {
            return computeFromResponsesOnly(req);
        }

        JsonNode instDef = null;
        for (JsonNode inst : instruments) {
            if (req.getInstrument().equals(inst.get("code").asText())) {
                instDef = inst;
                break;
            }
        }

        if (instDef == null) {
            return computeFromResponsesOnly(req);
        }

        // Build variable -> domain (subscale) mapping
        Map<String, String> varToDomain = new LinkedHashMap<>();
        JsonNode domains = instDef.get("domains");
        if (domains != null && domains.isObject()) {
            Iterator<String> fieldNames = domains.fieldNames();
            while (fieldNames.hasNext()) {
                String domainName = fieldNames.next();
                JsonNode vars = domains.get(domainName);
                if (vars != null && vars.isArray()) {
                    for (JsonNode v : vars) {
                        varToDomain.put(v.asText(), domainName);
                    }
                }
            }
        }

        double totalScore = 0.0;
        Map<String, Double> subscaleScores = new LinkedHashMap<>();

        if (req.getResponses() != null) {
            for (Map.Entry<String, String> entry : req.getResponses().entrySet()) {
                String varName = entry.getKey();
                String value = entry.getValue();
                try {
                    double score = Double.parseDouble(value);
                    totalScore += score;

                    String domain = varToDomain.getOrDefault(varName, "Other");
                    subscaleScores.merge(domain, score, Double::sum);
                } catch (NumberFormatException e) {
                    // Non-numeric responses are skipped in fallback mode
                    log.fine("Skipping non-numeric response for variable " + varName + ": " + value);
                }
            }
        }

        double maxScore = instDef.has("max_score") && !instDef.get("max_score").isNull()
                ? instDef.get("max_score").asDouble() : 0.0;
        double cutoff = instDef.has("cutoff") && !instDef.get("cutoff").isNull()
                ? instDef.get("cutoff").asDouble() : Double.NaN;

        ComputeResponse response = new ComputeResponse();
        response.setTotalScore(totalScore);
        response.setMaxScore(maxScore > 0 ? maxScore : null);
        response.setSubscaleScores(subscaleScores);

        if (!Double.isNaN(cutoff) && maxScore > 0) {
            response.setInterpretation(totalScore >= cutoff ? "正常范围" : "异常（低于临界值）");
        } else {
            response.setInterpretation(totalScore >= 0 ? "评分完成" : null);
        }

        return response;
    }

    /**
     * Fallback: compute total by summing numeric response values without metadata.
     */
    private ComputeResponse computeFromResponsesOnly(ComputeRequest req) {
        double totalScore = 0.0;
        if (req.getResponses() != null) {
            for (String value : req.getResponses().values()) {
                try {
                    totalScore += Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    // skip non-numeric
                }
            }
        }
        ComputeResponse response = new ComputeResponse();
        response.setTotalScore(totalScore);
        response.setMaxScore(null);
        response.setSubscaleScores(new LinkedHashMap<>());
        response.setInterpretation("评分完成（无元数据，仅数值求和）");
        return response;
    }

    /**
     * GET /api/v1/scales/visit-progress/{subjectId}
     * Returns completion status per visit per scale for a subject.
     * Queries scale_assessment via JdbcTemplate.
     */
    public VisitProgressResponse getVisitProgress(Long subjectId) {
        String sql = """
            SELECT
                COALESCE(s.visit_label, 'Unknown') AS visit_code,
                si.code AS scale_code,
                si.name AS scale_name,
                sa.data_entry_status AS status,
                sa.total_score AS total_score,
                sa.assessment_date AS assessment_date
            FROM scale_assessment sa
            JOIN scale_instrument si ON sa.instrument_id = si.id
            LEFT JOIN session s ON sa.session_id = s.id
            WHERE sa.subject_id = ?
            ORDER BY s.visit_label, si.code
            """;

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, subjectId);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to query visit progress, trying with assessment-only query: " + e.getMessage());
            // Fallback: query without session join (session table may be in a different schema)
            String fallbackSql = """
                SELECT
                    'Unknown' AS visit_code,
                    si.code AS scale_code,
                    si.name AS scale_name,
                    sa.data_entry_status AS status,
                    sa.total_score AS total_score,
                    sa.assessment_date AS assessment_date
                FROM scale_assessment sa
                JOIN scale_instrument si ON sa.instrument_id = si.id
                WHERE sa.subject_id = ?
                ORDER BY si.code
                """;
            rows = jdbcTemplate.queryForList(fallbackSql, subjectId);
        }

        VisitProgressResponse response = new VisitProgressResponse();
        response.setSubjectId(subjectId);

        // Build visit-name lookup from metadata
        Map<String, String> visitNameMap = new LinkedHashMap<>();
        if (metadataRoot != null) {
            JsonNode visits = metadataRoot.get("visits");
            if (visits != null && visits.isArray()) {
                for (JsonNode v : visits) {
                    visitNameMap.put(v.get("code").asText(), v.get("name").asText());
                }
            }
        }

        // Group rows by visit
        Map<String, VisitProgressResponse.VisitProgressItem> visitMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String visitCode = (String) row.get("visit_code");
            String visitName = visitNameMap.getOrDefault(visitCode, visitCode);

            visitMap.putIfAbsent(visitCode, newVisitProgressItem(visitCode, visitName));
            VisitProgressResponse.VisitProgressItem visitItem = visitMap.get(visitCode);

            ScaleProgressDTO sp = new ScaleProgressDTO();
            sp.setScaleCode((String) row.get("scale_code"));
            sp.setScaleName((String) row.get("scale_name"));
            sp.setStatus((String) row.get("status"));

            Object score = row.get("total_score");
            sp.setTotalScore(score != null ? ((Number) score).doubleValue() : null);

            Object dateObj = row.get("assessment_date");
            if (dateObj instanceof Date d) {
                sp.setLastAssessmentDate(d.toLocalDate());
            } else if (dateObj instanceof java.time.LocalDate ld) {
                sp.setLastAssessmentDate(ld);
            }

            if (visitItem.getScales() == null) {
                visitItem.setScales(new ArrayList<>());
            }
            visitItem.getScales().add(sp);
        }

        response.setVisits(new ArrayList<>(visitMap.values()));
        return response;
    }

    private VisitProgressResponse.VisitProgressItem newVisitProgressItem(String code, String name) {
        VisitProgressResponse.VisitProgressItem item = new VisitProgressResponse.VisitProgressItem();
        item.setVisitCode(code);
        item.setVisitName(name);
        item.setScales(new ArrayList<>());
        return item;
    }

    // ---------------------------------------------------------------
    // Score Center
    // ---------------------------------------------------------------

    /**
     * GET /api/v1/scales/scores/{subjectId}
     * Returns all scale scores for a subject across all visits with history,
     * subscales, interpretation, and flag for the Score Center UI.
     */
    public List<Map<String, Object>> getSubjectScores(Long subjectId) {
        String sql = """
            SELECT
                sa.id,
                si.code AS instrument,
                si.name AS name,
                si.name_zh AS fullName,
                sa.total_score AS totalScore,
                si.total_score_max AS maxScore,
                si.cutoff_score AS cutoffScore,
                sa.data_entry_status AS status,
                sa.assessment_date AS assessmentDate,
                sa.notes AS notes
            FROM scale_assessment sa
            JOIN scale_instrument si ON sa.instrument_id = si.id
            WHERE sa.subject_id = ? AND sa.data_entry_status = 'Complete'
            ORDER BY si.code, sa.assessment_date DESC
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, subjectId);

        // Group by instrument to build score records with history
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String instrument = (String) row.get("instrument");
            Map<String, Object> record = grouped.computeIfAbsent(instrument, k -> {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("instrument", instrument);
                rec.put("name", row.get("name"));
                rec.put("fullName", row.get("fullName"));
                rec.put("maxScore", row.get("maxScore"));
                rec.put("cutoffScore", row.get("cutoffScore"));
                rec.put("history", new ArrayList<Map<String, Object>>());
                rec.put("totalScore", row.get("totalScore"));
                return rec;
            });

            // Add history entry
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history = (List<Map<String, Object>>) record.get("history");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row.get("assessmentDate"));
            entry.put("score", row.get("totalScore"));
            entry.put("status", row.get("status"));
            history.add(entry);
        }

        // Second pass: add interpretation and flag for each instrument
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : grouped.values()) {
            String instrument = (String) record.get("instrument");
            Object totalScoreObj = record.get("totalScore");
            double totalScore = totalScoreObj instanceof Number n ? n.doubleValue() : 0;

            // Get instrument definition from metadata for cutoff/interpretation
            Map<String, Object> instDef = getInstrumentDef(instrument);
            double cutoff = instDef != null && instDef.containsKey("cutoff")
                    ? ((Number) instDef.get("cutoff")).doubleValue() : Double.NaN;
            double maxScore = record.get("maxScore") instanceof Number n ? n.doubleValue() : 100;

            // Determine flag and interpretation
            String flag = "normal";
            String interpretation = "";
            if (!Double.isNaN(cutoff) && maxScore > 0) {
                if (totalScore >= cutoff) { flag = "normal"; interpretation = "正常范围"; }
                else if (totalScore >= cutoff * 0.7) { flag = "borderline"; interpretation = "临界异常"; }
                else { flag = "abnormal"; interpretation = "显著异常"; }
            }

            record.put("totalScore", totalScore);
            record.put("flag", flag);
            record.put("interpretation", interpretation);
            record.put("cutoffNote", !Double.isNaN(cutoff) ? "截断值: " + cutoff : "");
            record.put("subscales", new ArrayList<>());
            record.put("interpretationText", "");

            result.add(record);
        }

        return result;
    }

    private Map<String, Object> getInstrumentDef(String code) {
        if (metadataRoot == null) return null;
        JsonNode instruments = metadataRoot.get("instruments");
        if (instruments == null || !instruments.isArray()) return null;
        for (JsonNode inst : instruments) {
            if (code.equals(inst.get("code").asText())) {
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("max_score", inst.has("max_score") ? inst.get("max_score").asDouble() : 0);
                def.put("cutoff", inst.has("cutoff") && !inst.get("cutoff").isNull()
                        ? inst.get("cutoff").asDouble() : Double.NaN);
                return def;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Assessments / Draft / Submit
    // ---------------------------------------------------------------

    public List<Map<String, Object>> listAssessments(Long subjectId, Long sessionId) {
        StringBuilder sql = new StringBuilder(
            "SELECT sa.*, si.name as instrumentName, si.code as instrumentCode " +
            "FROM scale_assessment sa LEFT JOIN scale_instrument si ON sa.instrument_id = si.id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (subjectId != null) { sql.append(" AND sa.subject_id = ?"); params.add(subjectId); }
        if (sessionId != null) { sql.append(" AND sa.session_id = ?"); params.add(sessionId); }
        sql.append(" ORDER BY sa.assessment_date DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @Transactional
    public void saveDraft(Map<String, Object> body) {
        persistVisitResponses(body, "Draft");
        updateTaskStatus(body, "IN_PROGRESS");
    }

    @Transactional
    public void submitResponses(Map<String, Object> body) {
        persistVisitResponses(body, "Complete");
        updateTaskStatus(body, "SCORED");
    }

    public void assertPatientTaskEditable(Long subjectId, Long sessionId) {
        if (subjectId == null || sessionId == null) {
            throw new IllegalArgumentException("患者作答必须关联有效访视");
        }
        List<String> statuses = jdbcTemplate.queryForList(
            "SELECT status FROM assessment_task WHERE subject_id=? AND session_id=?",
            String.class, subjectId, sessionId);
        if (statuses.isEmpty()) throw new IllegalArgumentException("当前访视没有分配给患者的量表任务");
        if (!Set.of("PENDING", "IN_PROGRESS", "RETURNED").contains(statuses.get(0))) {
            throw new IllegalStateException("量表已经提交；如需修改，请联系医生退回");
        }
    }

    public void returnPatientTask(Long taskId, String reason) {
        int updated = jdbcTemplate.update("""
            UPDATE assessment_task SET status='RETURNED',submitted_at=NULL,
                return_reason=?,returned_at=NOW()
            WHERE id=? AND status IN ('SUBMITTED','SCORED')
            """, reason, taskId);
        if (updated == 0) throw new IllegalStateException("只有已提交或已评分的任务可以退回");
    }

    public Long patientTaskSubject(Long taskId) {
        List<Long> subjects = jdbcTemplate.queryForList(
            "SELECT subject_id FROM assessment_task WHERE id=?", Long.class, taskId);
        if (subjects.isEmpty()) throw new IllegalArgumentException("量表任务不存在");
        return subjects.get(0);
    }

    public List<Map<String, Object>> patientTasks(Long subjectId) {
        return jdbcTemplate.queryForList("""
            SELECT t.id,t.subject_id AS subjectId,t.session_id AS sessionId,t.visit_code AS visitCode,
                   CASE
                     WHEN t.visit_code REGEXP '^V0*1$' THEN 'V0'
                     WHEN t.visit_code REGEXP '^V0*2$' THEN 'SF1'
                     WHEN t.visit_code REGEXP '^V0*3$' THEN 'SF2'
                     ELSE t.visit_code
                   END AS formCode,
                   t.status,t.assigned_at AS assignedAt,t.started_at AS startedAt,t.submitted_at AS submittedAt,
                   t.return_reason AS returnReason,t.returned_at AS returnedAt,
                   t.template_version_id AS templateVersionId,t.scale_codes AS scaleCodes,t.due_at AS dueAt,
                   s.session_date AS sessionDate
            FROM assessment_task t JOIN `session` s ON s.id=t.session_id
            WHERE t.subject_id=? ORDER BY s.session_date DESC,t.id DESC
            """, subjectId);
    }

    private void updateTaskStatus(Map<String, Object> body, String status) {
        Long subjectId = body.get("subjectId") instanceof Number n ? n.longValue() : null;
        Long sessionId = body.get("sessionId") instanceof Number n ? n.longValue() : null;
        String visitCode = Objects.toString(body.get("visitCode"), "");
        if (subjectId == null || visitCode.isBlank()) return;
        if ("SUBMITTED".equals(status) || "SCORED".equals(status)) {
            if (sessionId != null) jdbcTemplate.update("""
                UPDATE assessment_task SET status=?,
                    started_at=COALESCE(started_at,NOW()),submitted_at=NOW()
                WHERE subject_id=? AND session_id=?
                """, status, subjectId, sessionId);
            else jdbcTemplate.update("""
                UPDATE assessment_task SET status=?,
                    started_at=COALESCE(started_at,NOW()),submitted_at=NOW()
                WHERE subject_id=? AND visit_code=?
                """, status, subjectId, visitCode);
        } else {
            if (sessionId != null) jdbcTemplate.update("""
                UPDATE assessment_task SET status='IN_PROGRESS',started_at=COALESCE(started_at,NOW())
                WHERE subject_id=? AND session_id=? AND status='PENDING'
                """, subjectId, sessionId);
            else jdbcTemplate.update("""
                UPDATE assessment_task SET status='IN_PROGRESS',started_at=COALESCE(started_at,NOW())
                WHERE subject_id=? AND visit_code=? AND status='PENDING'
                """, subjectId, visitCode);
        }
    }

    public Map<String, Object> loadResponses(Long subjectId, String visitCode, Long requestedSessionId) {
        Long sessionId = requestedSessionId != null ? requestedSessionId : resolveSessionId(subjectId, visitCode);
        if (sessionId == null) return new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT sd.item_code, sd.response_value
            FROM scale_data sd
            JOIN scale_assessment sa ON sa.id = sd.assessment_id
            WHERE sa.subject_id = ? AND sa.session_id = ?
            ORDER BY sd.id
            """, subjectId, sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = Objects.toString(row.get("item_code"), "");
            String value = Objects.toString(row.get("response_value"), "null");
            if (code.isBlank()) continue;
            try {
                result.put(code, objectMapper.readValue(value, Object.class));
            } catch (Exception ignored) {
                result.put(code, value);
            }
        }
        return result;
    }

    private void persistVisitResponses(Map<String, Object> body, String status) {
        Long subjectId = body.get("subjectId") instanceof Number n ? n.longValue() : null;
        String visitCode = Objects.toString(body.get("visitCode"), "");
        @SuppressWarnings("unchecked")
        Map<String, Object> responses = body.get("responses") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : null;
        if (subjectId == null || visitCode.isBlank()) {
            throw new IllegalArgumentException("受试者和访视不能为空");
        }
        if (responses == null) throw new IllegalArgumentException("答题数据不能为空");

        Long sessionId = body.get("sessionId") instanceof Number n
            ? n.longValue() : resolveSessionId(subjectId, visitCode);
        if (sessionId == null) throw new IllegalArgumentException("未找到该受试者对应的访视：" + visitCode);

        Map<String, List<Map.Entry<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : responses.entrySet()) {
            String scaleCode = findScaleForItem(visitCode, entry.getKey());
            if (scaleCode == null) scaleCode = "VISIT_" + visitCode;
            grouped.computeIfAbsent(scaleCode, ignored -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<Map.Entry<String, Object>>> group : grouped.entrySet()) {
            long instrumentId = ensureInstrument(group.getKey(), visitCode);
            long assessmentId = ensureAssessment(subjectId, sessionId, instrumentId, status);
            jdbcTemplate.update("DELETE FROM scale_data WHERE assessment_id = ?", assessmentId);
            int itemNumber = 1;
            double totalScore = 0;
            for (Map.Entry<String, Object> response : group.getValue()) {
                String serialized;
                try {
                    serialized = objectMapper.writeValueAsString(response.getValue());
                } catch (Exception error) {
                    throw new IllegalArgumentException("题目 " + response.getKey() + " 无法保存", error);
                }
                Double itemScore = optionScore(visitCode, group.getKey(), response.getKey(), response.getValue());
                if (itemScore != null) totalScore += itemScore;
                jdbcTemplate.update("""
                    INSERT INTO scale_data
                        (assessment_id, instrument_id, item_code, item_number, response_value, item_score)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, assessmentId, instrumentId, response.getKey(), itemNumber++, serialized, itemScore);
            }
            jdbcTemplate.update("""
                UPDATE scale_assessment
                SET data_entry_status = ?, total_score = ?, assessment_date = CURDATE(),
                    is_completed = ?
                WHERE id = ?
                """, status, totalScore, "Complete".equals(status), assessmentId);
        }
    }

    private Long resolveSessionId(Long subjectId, String visitCode) {
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, visit_label FROM session WHERE subject_id = ? ORDER BY id", subjectId)) {
            String label = Objects.toString(row.get("visit_label"), "");
            if (normalizeVisitCode(label).equals(normalizeVisitCode(visitCode))) {
                return ((Number) row.get("id")).longValue();
            }
        }
        return null;
    }

    private static String normalizeVisitCode(String code) {
        if (code == null) return "";
        var matcher = java.util.regex.Pattern.compile("^([A-Za-z]+)0*(\\d+)$").matcher(code.trim());
        return matcher.matches()
                ? matcher.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group(2))
                : code.trim().toUpperCase(Locale.ROOT);
    }

    private String findScaleForItem(String visitCode, String itemCode) {
        JsonNode visit = findVisit(visitCode);
        if (visit == null || !visit.has("scales")) return null;
        for (JsonNode scale : visit.get("scales")) {
            JsonNode items = scale.get("items");
            if (items == null) continue;
            for (JsonNode item : items) {
                if (itemCode.equals(item.path("code").asText()) && belongsToVisit(itemCode, visitCode)) {
                    return scale.path("code").asText();
                }
            }
        }
        return null;
    }

    private JsonNode findVisit(String visitCode) {
        if (metadataRoot == null || !metadataRoot.has("visits")) return null;
        for (JsonNode visit : metadataRoot.get("visits")) {
            if (visitCode.equalsIgnoreCase(visit.path("code").asText())) return visit;
        }
        return null;
    }

    private long ensureInstrument(String scaleCode, String visitCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM scale_instrument WHERE code = ? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), scaleCode);
        if (!ids.isEmpty()) return ids.get(0);
        String name = scaleCode;
        JsonNode visit = findVisit(visitCode);
        if (visit != null && visit.has("scales")) {
            for (JsonNode scale : visit.get("scales")) {
                if (scaleCode.equals(scale.path("code").asText())) {
                    name = scale.path("name").asText(scaleCode);
                    break;
                }
            }
        }
        jdbcTemplate.update("""
            INSERT INTO scale_instrument (code, name, name_zh, category, is_active)
            VALUES (?, ?, ?, 'VISIT_FORM', 1)
            """, scaleCode, name, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM scale_instrument WHERE code = ?", Long.class, scaleCode);
    }

    private long ensureAssessment(Long subjectId, Long sessionId, long instrumentId, String status) {
        List<Long> ids = jdbcTemplate.query("""
            SELECT id FROM scale_assessment
            WHERE subject_id = ? AND session_id = ? AND instrument_id = ?
            ORDER BY id DESC LIMIT 1
            """, (rs, rowNum) -> rs.getLong(1), subjectId, sessionId, instrumentId);
        if (!ids.isEmpty()) return ids.get(0);
        jdbcTemplate.update("""
            INSERT INTO scale_assessment
                (subject_id, session_id, instrument_id, total_score, data_entry_status,
                 assessment_date, is_completed)
            VALUES (?, ?, ?, 0, ?, CURDATE(), ?)
            """, subjectId, sessionId, instrumentId, status, "Complete".equals(status));
        return jdbcTemplate.queryForObject("""
            SELECT id FROM scale_assessment
            WHERE subject_id = ? AND session_id = ? AND instrument_id = ?
            ORDER BY id DESC LIMIT 1
            """, Long.class, subjectId, sessionId, instrumentId);
    }

    private Double optionScore(String visitCode, String scaleCode, String itemCode, Object value) {
        JsonNode visit = findVisit(visitCode);
        if (visit == null || !visit.has("scales")) return null;
        for (JsonNode scale : visit.get("scales")) {
            if (!scaleCode.equals(scale.path("code").asText())) continue;
            for (JsonNode item : scale.path("items")) {
                if (!itemCode.equals(item.path("code").asText())) continue;
                String itemName = item.path("name").asText();
                if (itemCode.matches("(?i).*_(LAN|LANGUAGE)$") || itemName.contains("语言")) return null;
                for (JsonNode option : item.path("options")) {
                    if (Objects.toString(value, "").equals(option.path("code").asText())) {
                        return option.path("score").asDouble();
                    }
                }
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // DTO mappers
    // ---------------------------------------------------------------

    private InstrumentDTO toDTO(ScaleInstrument i) {
        InstrumentDTO d = new InstrumentDTO();
        d.setId(i.getId()); d.setCode(i.getCode()); d.setName(i.getName());
        d.setNameZh(i.getNameZh()); d.setAbbreviation(i.getAbbreviation());
        d.setVersion(i.getVersion()); d.setCategory(i.getCategory());
        d.setDescription(i.getDescription()); d.setTotalScoreMin(i.getTotalScoreMin());
        d.setTotalScoreMax(i.getTotalScoreMax()); d.setCutoffScore(i.getCutoffScore());
        d.setAdministrationTimeMin(i.getAdministrationTimeMin());
        return d;
    }
    private ItemDTO toItemDTO(ScaleItem i) {
        ItemDTO d = new ItemDTO();
        d.setId(i.getId()); d.setInstrumentId(i.getInstrumentId());
        d.setItemIndex(i.getItemIndex()); d.setDomainName(i.getDomainName());
        d.setQuestionText(i.getQuestionText()); d.setInputType(i.getInputType());
        d.setOptions(i.getOptions()); d.setMaxScore(i.getMaxScore()); d.setScoreType(i.getScoreType());
        return d;
    }
    private AssessmentDTO toAssessmentDTO(ScaleAssessment a) {
        AssessmentDTO d = new AssessmentDTO();
        d.setId(a.getId()); d.setSessionId(a.getSessionId()); d.setSubjectId(a.getSubjectId());
        d.setInstrumentId(a.getInstrumentId()); d.setExaminerId(a.getExaminerId());
        d.setAssessmentDate(a.getAssessmentDate()); d.setTotalScore(a.getTotalScore());
        d.setDataEntryStatus(a.getDataEntryStatus()); d.setAdministrationMode(a.getAdministrationMode());
        d.setNotes(a.getNotes()); d.setCreatedAt(a.getCreatedAt());
        return d;
    }
}
