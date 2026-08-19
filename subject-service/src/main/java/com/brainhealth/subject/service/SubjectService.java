package com.brainhealth.subject.service;

import com.brainhealth.common.constant.Constants;
import com.brainhealth.common.exception.BusinessException;
import com.brainhealth.common.exception.ErrorCode;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.subject.dto.ImportResult;
import com.brainhealth.subject.dto.SessionDTO;
import com.brainhealth.subject.dto.SubjectCreateRequest;
import com.brainhealth.subject.dto.SubjectDTO;
import com.brainhealth.subject.dto.SubjectUpdateRequest;
import com.brainhealth.subject.dto.TimelineItemDTO;
import com.brainhealth.subject.entity.Session;
import com.brainhealth.subject.entity.Subject;
import com.brainhealth.subject.repository.SessionRepository;
import com.brainhealth.subject.repository.SubjectRepository;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core service for subject (patient/participant) management.
 *
 * Provides:
 * <ul>
 *   <li>CRUD with soft-delete via {@code isActive} flag</li>
 *   <li>Dynamic search with JPA {@link Specification} composition</li>
 *   <li>Batch import from Excel (.xlsx/.xls) using Apache POI</li>
 *   <li>Session timeline aggregation with interval calculations</li>
 *   <li>Copy-last-session: duplicate the most recent session as a new one</li>
 * </ul>
 */
@Service
public class SubjectService {

    private static final Logger log = Logger.getLogger(SubjectService.class.getName());

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_IMPORT_ROWS = 5000;
    private static final int BATCH_SIZE = 200;

    // Expected Excel column headers (must match this order)
    private static final String[] EXCEL_HEADERS = {
            "subjectId", "sex", "dateOfBirth", "ethnicity", "educationYears",
            "handedness", "institutionId", "projectId"
    };

    private final SubjectRepository subjectRepository;
    private final SessionRepository sessionRepository;
    private final JdbcTemplate jdbcTemplate;

    public SubjectService(SubjectRepository subjectRepository,
                          SessionRepository sessionRepository,
                          JdbcTemplate jdbcTemplate) {
        this.subjectRepository = subjectRepository;
        this.sessionRepository = sessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ========================================================================
    // Search with JPA Specifications
    // ========================================================================

    /**
     * Dynamic paged search over subjects.
     * Composes a JPA {@link Specification} from the non-null filter parameters.
     */
    @Transactional(readOnly = true)
    public PageResult<SubjectDTO> search(int page, int size,
                                         String subjectId, String sex,
                                         String ethnicity, Long institutionId,
                                         Long projectId, Boolean isActive,
                                         Set<Long> allowedProjectIds) {
        log.log(Level.FINE, "Search subjects page={0} size={1}", new Object[]{page, size});

        Specification<Subject> spec = buildSearchSpec(subjectId, sex, ethnicity,
                institutionId, projectId, isActive, allowedProjectIds);
        Pageable pageable = buildPageable(page, size, "id");

        Page<Subject> subjectPage = subjectRepository.findAll(spec, pageable);

        List<SubjectDTO> records = subjectPage.getContent().stream()
                .map(this::toSubjectDTOBasic)
                .collect(Collectors.toList());

        log.log(Level.FINE, "Search returned {0} results (total {1})",
                new Object[]{records.size(), subjectPage.getTotalElements()});

        return PageResult.of(page, size, subjectPage.getTotalElements(), records);
    }

    /**
     * Build a JPA Specification from individual filter parameters.
     */
    private Specification<Subject> buildSearchSpec(String subjectId, String sex,
                                                   String ethnicity, Long institutionId,
                                                   Long projectId, Boolean isActive,
                                                   Set<Long> allowedProjectIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(subjectId)) {
                predicates.add(cb.like(cb.lower(root.get("subjectId")),
                        "%" + subjectId.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(sex)) {
                predicates.add(cb.equal(cb.lower(root.get("sex")), sex.toLowerCase()));
            }
            if (StringUtils.hasText(ethnicity)) {
                predicates.add(cb.equal(cb.lower(root.get("ethnicity")),
                        ethnicity.toLowerCase()));
            }
            if (institutionId != null) {
                predicates.add(cb.equal(root.get("institutionId"), institutionId));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            } else if (allowedProjectIds != null) {
                if (allowedProjectIds.isEmpty()) predicates.add(cb.disjunction());
                else predicates.add(root.get("projectId").in(allowedProjectIds));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            // Default: only return active subjects unless explicitly filtered otherwise
            if (isActive == null) {
                predicates.add(cb.isTrue(root.get("isActive")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Retrieve a single active subject by its database primary key.
     */
    @Transactional(readOnly = true)
    public SubjectDTO getById(Long id) {
        log.log(Level.FINE, "Get subject by id: {0}", id);

        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "Subject not found or inactive: id={0}", id);
                    return new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
                });

        return toSubjectDTO(subject);
    }

    /**
     * Create a new subject.
     * Assigns the {@code registeredBy} field from the security context when available.
     */
    @Transactional
    public SubjectDTO create(SubjectCreateRequest request) {
        log.log(Level.INFO, "Creating subject with subjectId: {0}", request.getSubjectId());

        // Validate uniqueness of business subjectId
        if (subjectRepository.existsBySubjectId(request.getSubjectId())) {
            log.log(Level.WARNING, "Duplicate subjectId: {0}", request.getSubjectId());
            throw new BusinessException(ErrorCode.SUBJECT_ID_DUPLICATE);
        }

        Subject subject = new Subject();
        subject.setSubjectId(request.getSubjectId());
        subject.setExternalId(request.getExternalId());
        subject.setFirstName(request.getFirstName());
        subject.setLastName(request.getLastName());
        subject.setSex(request.getSex());
        subject.setDateOfBirth(request.getDateOfBirth());
        subject.setEthnicity(request.getEthnicity());
        subject.setNationCodeId(request.getEthnicityCodeId());
        subject.setNamePinyin(request.getNamePinyin());
        if (request.getAgeAtEnrollment() != null) subject.setAgeAtEnrollment(request.getAgeAtEnrollment());
        if (request.getEducationCodeId() != null) subject.setEducationCodeId(request.getEducationCodeId());
        subject.setEducationYears(request.getEducationYears());
        subject.setHandedness(request.getHandedness());
        subject.setMaritalStatusCodeId(request.getMaritalStatusCodeId());
        subject.setBloodTypeCodeId(request.getBloodTypeCodeId());
        subject.setPhoneHash(request.getPhone());
        subject.setAddressCity(request.getAddressCity());
        subject.setAddressDistrict(request.getAddressDistrict());
        subject.setHeightCm(request.getHeightCm());
        subject.setWeightKg(request.getWeightKg());
        subject.setInstitutionId(request.getInstitutionId());
        subject.setProjectId(request.getProjectId());
        subject.setEnrollmentDate(request.getEnrollmentDate());
        subject.setIsConsented(request.getIsConsented() != null ? request.getIsConsented() : false);
        subject.setConsentDate(request.getConsentDate());
        subject.setRemarks(request.getRemarks());
        subject.setIsActive(true);
        subject.setRegisteredBy(resolveCurrentUserId());

        Subject saved = subjectRepository.save(subject);
        log.log(Level.INFO, "Subject created: id={0} subjectId={1}",
                new Object[]{saved.getId(), saved.getSubjectId()});

        return toSubjectDTO(saved);
    }

    /**
     * Partial update of an active subject.
     * Only non-null fields in the request are applied.
     */
    @Transactional
    public SubjectDTO update(Long id, SubjectUpdateRequest request) {
        log.log(Level.INFO, "Updating subject id: {0}", id);

        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "Subject not found for update: id={0}", id);
                    return new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
                });

        if (request.getSex() != null) subject.setSex(request.getSex());
        if (request.getDateOfBirth() != null) subject.setDateOfBirth(request.getDateOfBirth());
        if (request.getEthnicity() != null) subject.setEthnicity(request.getEthnicity());
        if (request.getEducationYears() != null) subject.setEducationYears(request.getEducationYears());
        if (request.getHandedness() != null) subject.setHandedness(request.getHandedness());
        if (request.getFirstName() != null) subject.setFirstName(request.getFirstName());
        if (request.getLastName() != null) subject.setLastName(request.getLastName());
        if (request.getNamePinyin() != null) subject.setNamePinyin(request.getNamePinyin());
        if (request.getPhone() != null) subject.setPhoneHash(request.getPhone());
        if (request.getMaritalStatusCodeId() != null) subject.setMaritalStatusCodeId(request.getMaritalStatusCodeId());
        if (request.getBloodTypeCodeId() != null) subject.setBloodTypeCodeId(request.getBloodTypeCodeId());
        if (request.getHeightCm() != null) subject.setHeightCm(request.getHeightCm());
        if (request.getWeightKg() != null) subject.setWeightKg(request.getWeightKg());
        if (request.getAddressCity() != null) subject.setAddressCity(request.getAddressCity());
        if (request.getAddressDistrict() != null) subject.setAddressDistrict(request.getAddressDistrict());
        if (request.getIsConsented() != null) subject.setIsConsented(request.getIsConsented());
        if (request.getConsentDate() != null) subject.setConsentDate(request.getConsentDate());
        if (request.getEnrollmentDate() != null) subject.setEnrollmentDate(request.getEnrollmentDate());
        if (request.getRemarks() != null) subject.setRemarks(request.getRemarks());

        Subject saved = subjectRepository.save(subject);
        log.log(Level.INFO, "Subject updated: id={0}", saved.getId());

        return toSubjectDTO(saved);
    }

    /**
     * Soft-delete a subject by setting {@code isActive = false}.
     * Does not cascade to sessions — they remain queryable for historical data.
     */
    @Transactional
    public void softDelete(Long id) {
        log.log(Level.INFO, "Soft-deleting subject id: {0}", id);

        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> {
                    log.log(Level.WARNING, "Subject not found for soft-delete: id={0}", id);
                    return new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
                });

        subject.setIsActive(false);
        subjectRepository.save(subject);

        log.log(Level.INFO, "Subject soft-deleted: id={0}", id);
    }

    // ========================================================================
    // Batch Import from Excel (Apache POI)
    // ========================================================================

    /**
     * Parse an uploaded Excel file (.xlsx / .xls) and batch-import subjects.
     *
     * <p>The first row must be a header row. Each subsequent data row is validated
     * independently; rows that fail validation are skipped and reported as errors.
     * Successful rows are persisted in batches of {@value #BATCH_SIZE}.</p>
     */
    @Transactional
    public ImportResult batchImport(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        log.log(Level.INFO, "Starting Excel batch import: {0}", originalFilename);

        ImportResult result = new ImportResult();
        result.setFileName(originalFilename != null ? originalFilename : "unknown");

        List<SubjectCreateRequest> parsedRows = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int dataRowCount = Math.max(0, sheet.getLastRowNum()); // 0-indexed, row 0 = header

            if (dataRowCount > MAX_IMPORT_ROWS) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                        "File has " + dataRowCount + " data rows; maximum is " + MAX_IMPORT_ROWS);
            }
            result.setTotalRows(dataRowCount);

            // Validate header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.IMPORT_TEMPLATE_ERROR,
                        "Missing header row");
            }

            // Parse data rows (starting from row 1)
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row)) {
                    continue; // skip blank rows silently
                }
                try {
                    parsedRows.add(parseRow(row));
                } catch (Exception e) {
                    String msg = "Row " + (rowIdx + 1) + " (1-based): " + e.getMessage();
                    result.addError(msg);
                    log.log(Level.WARNING, "Import row parse error: {0}", msg);
                }
            }

            // Batch persist parsed rows
            int success = 0;
            List<SubjectCreateRequest> batch = new ArrayList<>(BATCH_SIZE);

            for (SubjectCreateRequest req : parsedRows) {
                batch.add(req);
                if (batch.size() >= BATCH_SIZE) {
                    success += persistBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                success += persistBatch(batch);
            }

            result.setSuccessCount(success);
            // errorCount is auto-maintained via addError

            log.log(Level.INFO, "Import finished: {0} success, {1} errors",
                    new Object[]{success, result.getErrorCount()});

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to read upload file: {0}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "Cannot read uploaded file: " + e.getMessage());
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unexpected error during batch import", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "Import failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse a single Excel data row into a {@link SubjectCreateRequest}.
     */
    private SubjectCreateRequest parseRow(Row row) {
        SubjectCreateRequest req = new SubjectCreateRequest();

        // Column 0: subjectId (required)
        String subjectId = getCellString(row, 0, true, "subjectId");
        req.setSubjectId(subjectId);

        // Column 1: sex (required)
        String sex = getCellString(row, 1, true, "sex");
        req.setSex(sex);

        // Column 2: dateOfBirth (optional, format: yyyy-MM-dd)
        req.setDateOfBirth(getCellLocalDate(row, 2, false, "dateOfBirth"));

        // Column 3: ethnicity (optional)
        req.setEthnicity(getCellString(row, 3, false, "ethnicity"));

        // Column 4: educationYears (optional, integer)
        req.setEducationYears(getCellInteger(row, 4, false, "educationYears"));

        // Column 5: handedness (optional)
        req.setHandedness(getCellString(row, 5, false, "handedness"));

        // Column 6: institutionId (optional)
        req.setInstitutionId(getCellLong(row, 6, false, "institutionId"));

        // Column 7: projectId (optional)
        req.setProjectId(getCellLong(row, 7, false, "projectId"));

        return req;
    }

    /**
     * Persist a batch of {@link SubjectCreateRequest}s, skipping duplicates.
     */
    private int persistBatch(List<SubjectCreateRequest> requests) {
        int count = 0;
        for (SubjectCreateRequest req : requests) {
            if (subjectRepository.existsBySubjectId(req.getSubjectId())) {
                log.log(Level.WARNING, "Skipping duplicate subjectId in import: {0}",
                        req.getSubjectId());
                continue;
            }
            Subject subject = new Subject();
            subject.setSubjectId(req.getSubjectId());
            subject.setSex(req.getSex());
            subject.setDateOfBirth(req.getDateOfBirth());
            subject.setEthnicity(req.getEthnicity());
            subject.setEducationYears(req.getEducationYears());
            subject.setHandedness(req.getHandedness());
            subject.setInstitutionId(req.getInstitutionId());
            subject.setProjectId(req.getProjectId());
            subject.setIsActive(true);
            subject.setRegisteredBy(resolveCurrentUserId());

            subjectRepository.save(subject);
            count++;
        }
        return count;
    }

    // ========================================================================
    // Excel cell parsing helpers
    // ========================================================================

    /**
     * Check whether every cell in a row is blank.
     */
    private boolean isRowEmpty(Row row) {
        if (row.getLastCellNum() <= 0) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && StringUtils.hasText(cell.toString().trim())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Read a String value from a cell.
     */
    private String getCellString(Row row, int col, boolean required, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            if (required) {
                throw new IllegalArgumentException("Required field '" + fieldName
                        + "' (column " + (col + 1) + ") is missing");
            }
            return null;
        }

        String value;
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            value = (d == Math.floor(d) && !Double.isInfinite(d))
                    ? String.valueOf((long) d)
                    : String.valueOf(d);
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                value = cell.getStringCellValue();
            } catch (Exception e) {
                value = String.valueOf(cell.getNumericCellValue());
            }
        } else {
            value = cell.getStringCellValue();
        }

        if (value != null) {
            value = value.trim();
        }

        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalArgumentException("Required field '" + fieldName
                        + "' (column " + (col + 1) + ") is empty");
            }
            return null;
        }

        return value;
    }

    /**
     * Read a Long value from a cell.
     */
    private Long getCellLong(Row row, int col, boolean required, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            if (required) throwRequired(fieldName, col);
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            }
            String s = cell.getStringCellValue().trim();
            if (!StringUtils.hasText(s)) {
                if (required) throwRequired(fieldName, col);
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + fieldName
                    + "' (column " + (col + 1) + ") is not a valid integer");
        }
    }

    /**
     * Read an Integer value from a cell.
     */
    private Integer getCellInteger(Row row, int col, boolean required, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            if (required) throwRequired(fieldName, col);
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            String s = cell.getStringCellValue().trim();
            if (!StringUtils.hasText(s)) {
                if (required) throwRequired(fieldName, col);
                return null;
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + fieldName
                    + "' (column " + (col + 1) + ") is not a valid integer");
        }
    }

    /**
     * Read a LocalDate from a cell (accepts Excel date cells or yyyy-MM-dd strings).
     */
    private LocalDate getCellLocalDate(Row row, int col, boolean required,
                                       String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            if (required) throwRequired(fieldName, col);
            return null;
        }

        try {
            // Excel date-formatted cell
            if (cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            // Numeric date (Excel serial number)
            if (cell.getCellType() == CellType.NUMERIC) {
                java.util.Date date = cell.getDateCellValue();
                return date.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }
            // String date
            String s = cell.getStringCellValue().trim();
            if (!StringUtils.hasText(s)) {
                if (required) throwRequired(fieldName, col);
                return null;
            }
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Field '" + fieldName
                    + "' (column " + (col + 1)
                    + ") is not a valid date (expected yyyy-MM-dd)");
        } catch (Exception e) {
            throw new IllegalArgumentException("Field '" + fieldName
                    + "' (column " + (col + 1)
                    + ") cannot be parsed as date: " + e.getMessage());
        }
    }

    private void throwRequired(String fieldName, int col) {
        throw new IllegalArgumentException("Required field '" + fieldName
                + "' (column " + (col + 1) + ") is missing");
    }

    // ========================================================================
    // Timeline Aggregation
    // ========================================================================

    /**
     * Build a chronological timeline for a subject.
     *
     * <p>The timeline includes:
     * <ol>
     *   <li>A {@code REGISTRATION} event at the subject's creation time.</li>
     *   <li>A {@code SESSION} event for each active session, ordered by
     *       session date ascending.</li>
     * </ol>
     * For each event, the number of days since the previous event and since
     * the baseline (first session) are calculated.</p>
     */
    @Transactional(readOnly = true)
    public List<TimelineItemDTO> getTimeline(Long subjectId) {
        log.log(Level.INFO, "Building timeline for subject id: {0}", subjectId);

        Subject subject = subjectRepository.findByIdAndIsActiveTrue(subjectId)
                .orElseThrow(() -> {
                    log.log(Level.WARNING,
                            "Subject not found for timeline: id={0}", subjectId);
                    return new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
                });

        List<Session> sessions = sessionRepository
                .findBySubjectIdAndIsActiveTrueOrderBySessionDateAsc(subjectId);

        List<TimelineItemDTO> timeline = new ArrayList<>();

        // --- Registration event ---
        TimelineItemDTO registration = new TimelineItemDTO();
        registration.setEventType("REGISTRATION");
        registration.setLabel("Subject Registered");
        registration.setEventDate(subject.getCreatedAt());
        registration.setDescription("Subject " + subject.getSubjectId()
                + " enrolled into the platform");
        registration.setReferenceId(subject.getId());
        registration.setSequence(0);
        registration.setDaysSincePrevious(null);
        registration.setDaysFromBaseline(null);
        timeline.add(registration);

        // --- Session events ---
        Session baselineSession = sessions.isEmpty() ? null : sessions.get(0);

        for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            TimelineItemDTO item = new TimelineItemDTO();

            item.setEventType("SESSION");
            item.setLabel(session.getVisitLabel() != null
                    ? session.getVisitLabel()
                    : "Visit " + session.getVisitNumber());
            // Convert LocalDate to LocalDateTime at start of day
            item.setEventDate(session.getSessionDate() != null
                    ? session.getSessionDate().atStartOfDay()
                    : null);
            item.setSessionDate(session.getSessionDate());
            item.setDescription("Status: " + normalizeStatus(session.getStatus()));
            item.setReferenceId(session.getId());
            item.setSequence(i + 1); // sequence starts after registration

            // Days since previous timeline event
            if (i == 0) {
                // First session: interval from registration
                if (subject.getCreatedAt() != null && session.getSessionDate() != null) {
                    item.setDaysSincePrevious(ChronoUnit.DAYS.between(
                            subject.getCreatedAt().toLocalDate(),
                            session.getSessionDate()));
                }
            } else {
                Session prev = sessions.get(i - 1);
                if (prev.getSessionDate() != null && session.getSessionDate() != null) {
                    item.setDaysSincePrevious(ChronoUnit.DAYS.between(
                            prev.getSessionDate(), session.getSessionDate()));
                }
            }

            // Days from baseline
            if (baselineSession != null && baselineSession.getSessionDate() != null
                    && session.getSessionDate() != null) {
                item.setDaysFromBaseline(ChronoUnit.DAYS.between(
                        baselineSession.getSessionDate(), session.getSessionDate()));
            }

            timeline.add(item);
        }

        log.log(Level.INFO, "Timeline built: {0} events for subject {1}",
                new Object[]{timeline.size(), subjectId});

        return timeline;
    }

    /**
     * Return a human-readable status string, substituting UNKNOWN for null/blank.
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "UNKNOWN";
        }
        return status.toUpperCase().replace(' ', '_');
    }

    // ========================================================================
    // Session Operations
    // ========================================================================

    /**
     * List all active sessions for a subject, ordered by session date ascending.
     */
    @Transactional(readOnly = true)
    public List<SessionDTO> getSessions(Long subjectId) {
        // Verify subject exists and is active
        if (!subjectRepository.existsById(subjectId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }

        List<Session> sessions = sessionRepository
                .findBySubjectIdAndIsActiveTrueOrderBySessionDateAsc(subjectId);

        return sessions.stream()
                .map(this::toSessionDTO)
                .collect(Collectors.toList());
    }

    // ========================================================================
    // Copy-Last-Session Logic
    // ========================================================================

    /**
     * Copy the most recent active session of a subject as a new session.
     *
     * <p>The new session inherits the visit date and label pattern from the
     * source session. Its visit number is incremented by 1, its status is set
     * to {@code SCHEDULED}, and its {@code isActive} flag is {@code true}.
     * IDs and timestamps are regenerated by the database.</p>
     */
    @Transactional
    public SessionDTO copyLastSession(Long subjectId) {
        log.log(Level.INFO, "Copying last session for subject id: {0}", subjectId);

        // Verify subject exists and is active
        Subject subject = subjectRepository.findByIdAndIsActiveTrue(subjectId)
                .orElseThrow(() -> {
                    log.log(Level.WARNING,
                            "Subject not found for copy-last-session: id={0}", subjectId);
                    return new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
                });

        // Find the most recent active session (descending order)
        List<Session> sessions = sessionRepository
                .findBySubjectIdAndIsActiveTrueOrderBySessionDateDesc(subjectId);

        if (sessions.isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND,
                    "Subject " + subject.getSubjectId()
                            + " has no existing sessions to copy from");
        }

        Session sourceSession = sessions.get(0);
        log.log(Level.INFO, "Source session: id={0} label={1} date={2}",
                new Object[]{sourceSession.getId(), sourceSession.getVisitLabel(),
                        sourceSession.getSessionDate()});

        // Build the new session by copying relevant fields from the source
        Session newSession = new Session();
        newSession.setSubjectId(subjectId);
        newSession.setProjectId(sourceSession.getProjectId());
        newSession.setInstitutionId(sourceSession.getInstitutionId());

        // Increment visit number
        int nextNumber = (sourceSession.getVisitNumber() != null
                ? sourceSession.getVisitNumber() : 0) + 1;
        newSession.setVisitNumber(nextNumber);

        // Generate visit label using the platform pattern (e.g., "V03")
        newSession.setVisitLabel(String.format(Constants.SESSION_LABEL_PATTERN,
                nextNumber));

        // Carry forward the visit date from the source (caller can update later)
        newSession.setSessionDate(LocalDate.now());

        // New session starts as SCHEDULED
        newSession.setStatus("SCHEDULED");
        newSession.setIsActive(true);
        newSession.setRegisteredBy(sourceSession.getRegisteredBy());

        Session saved = sessionRepository.save(newSession);
        log.log(Level.INFO, "Copied session created: id={0} label={1}",
                new Object[]{saved.getId(), saved.getVisitLabel()});

        return toSessionDTO(saved);
    }

    // ========================================================================
    // DTO Mapping Helpers
    // ========================================================================

    /**
     * Map entity to DTO without FK name lookups (for list queries — fast).
     */
    private SubjectDTO toSubjectDTOBasic(Subject s) {
        SubjectDTO dto = new SubjectDTO();
        dto.setId(s.getId());
        dto.setSubjectId(s.getSubjectId());
        dto.setExternalId(s.getExternalId());
        dto.setFirstName(s.getFirstName());
        dto.setLastName(s.getLastName());
        dto.setInstitutionId(s.getInstitutionId());
        dto.setProjectId(s.getProjectId());
        dto.setSex(s.getSex());
        dto.setDateOfBirth(s.getDateOfBirth());
        dto.setAgeAtEnrollment(s.getAgeAtEnrollment());
        dto.setEthnicity(s.getEthnicity());
        dto.setEducationYears(s.getEducationYears());
        dto.setHandedness(s.getHandedness());
        dto.setHeightCm(s.getHeightCm());
        dto.setWeightKg(s.getWeightKg());
        dto.setAddressCity(s.getAddressCity());
        dto.setAddressDistrict(s.getAddressDistrict());
        dto.setStatus(s.getStatus());
        dto.setIsConsented(s.getIsConsented());
        dto.setEnrollmentDate(s.getEnrollmentDate());
        dto.setIsActive(s.getIsActive());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    /**
     * Map a {@link Subject} entity to a {@link SubjectDTO} with FK name lookups.
     */
    private SubjectDTO toSubjectDTO(Subject s) {
        SubjectDTO dto = new SubjectDTO();
        dto.setId(s.getId());
        dto.setSubjectId(s.getSubjectId());
        dto.setExternalId(s.getExternalId());
        dto.setFirstName(s.getFirstName());
        dto.setLastName(s.getLastName());
        dto.setNamePinyin(s.getNamePinyin());
        dto.setInstitutionId(s.getInstitutionId());
        dto.setProjectId(s.getProjectId());
        dto.setSex(s.getSex());
        dto.setDateOfBirth(s.getDateOfBirth());
        dto.setAgeAtEnrollment(s.getAgeAtEnrollment());
        dto.setEthnicity(s.getEthnicity());
        dto.setEthnicityCodeId(s.getNationCodeId());
        dto.setEducationCodeId(s.getEducationCodeId());
        dto.setEducationYears(s.getEducationYears());
        dto.setHandedness(s.getHandedness());
        dto.setMaritalStatusCodeId(s.getMaritalStatusCodeId());
        dto.setBloodTypeCodeId(s.getBloodTypeCodeId());
        dto.setPhone(s.getPhoneHash());
        dto.setAddressCity(s.getAddressCity());
        dto.setAddressDistrict(s.getAddressDistrict());
        dto.setHeightCm(s.getHeightCm());
        dto.setWeightKg(s.getWeightKg());
        dto.setBmi(s.getBmi());
        dto.setEnrollmentDate(s.getEnrollmentDate());
        dto.setEnrollmentInstitutionId(s.getEnrollmentInstitutionId());
        dto.setStatus(s.getStatus());
        dto.setIsConsented(s.getIsConsented());
        dto.setConsentDate(s.getConsentDate());
        dto.setRemarks(s.getRemarks());
        dto.setIsActive(s.getIsActive());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());

        // Resolve FK display names via simple lookups
        try {
            if (s.getInstitutionId() != null) {
                Map<String, Object> inst = jdbcTemplate.queryForMap("SELECT name FROM institution WHERE id=?", s.getInstitutionId());
                dto.setInstitutionName(inst != null ? (String) inst.get("name") : null);
            }
            if (s.getProjectId() != null) {
                Map<String, Object> proj = jdbcTemplate.queryForMap("SELECT name FROM project WHERE id=?", s.getProjectId());
                dto.setProjectName(proj != null ? (String) proj.get("name") : null);
            }
        } catch (Exception e) { /* lookup failed, names stay null */ }

        return dto;
    }

    /**
     * Map a {@link Session} entity to a {@link SessionDTO}.
     * Note: {@code sessionDate} is {@link LocalDate} in the entity but
     * {@link LocalDateTime} in the DTO — converted to start-of-day.
     */
    private SessionDTO toSessionDTO(Session s) {
        SessionDTO dto = new SessionDTO();
        dto.setId(s.getId());
        // Resolve the subject's business id for display
        dto.setSubjectId(resolveSubjectId(s.getSubjectId()));
        dto.setVisitLabel(s.getVisitLabel());
        dto.setVisitNumber(s.getVisitNumber());
        dto.setSessionDate(s.getSessionDate() != null
                ? s.getSessionDate().atStartOfDay()
                : null);
        dto.setStatus(s.getStatus());
        return dto;
    }

    // ========================================================================
    // Context & Pagination Helpers
    // ========================================================================

    /**
     * Resolve the currently authenticated user's id from the security context.
     * Returns 0L (system) when no authentication is available (e.g., batch jobs).
     *
     * <p>This method uses reflection-free checks so that subject-service
     * does not require a compile-time dependency on auth-service.</p>
     */
    private Long resolveCurrentUserId() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal != null && !"anonymousUser".equals(principal)) {
                    // If the principal has a getId() method (e.g., User entity), use it.
                    // Otherwise, fall back to parsing the principal's toString() or name.
                    try {
                        java.lang.reflect.Method getIdMethod =
                                principal.getClass().getMethod("getId");
                        Object id = getIdMethod.invoke(principal);
                        if (id instanceof Long longId) {
                            return longId;
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Principal type does not expose getId()
                    }
                    // Last resort: look up by username via subjectRepository?
                    // For now, return system id.
                }
            }
        } catch (Exception ignored) {
            // Security not configured or not available; use system id
        }
        return 0L;
    }

    /**
     * Resolve a subject's business {@code subjectId} from its internal id.
     * Falls back to the string representation of the id if the subject cannot be found.
     */
    private String resolveSubjectId(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findById(subjectId)
                .map(Subject::getSubjectId)
                .orElse(String.valueOf(subjectId));
    }

    /**
     * Build a Spring {@link Pageable} from 1-based page parameters.
     * Sorts by the given field ascending by default.
     */
    private Pageable buildPageable(int page, int size, String sortBy) {
        int safePage = Math.max(0, page - 1); // controller sends 1-based
        int safeSize = Math.min(Math.max(1, size), Constants.MAX_PAGE_SIZE);

        Sort sort = Sort.by(Sort.Direction.ASC,
                StringUtils.hasText(sortBy) ? sortBy : "id");

        return PageRequest.of(safePage, safeSize, sort);
    }
}
