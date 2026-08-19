-- ============================================================================
-- V004__imaging.sql
-- Imaging module: modality, scanner, session, series, EEG, report, order
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. imaging_modality — code table for imaging / electrophysiology modalities
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_modality (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    code            VARCHAR(32)  NOT NULL
        COMMENT 'Unique short code (e.g. MRI_T1, fMRI_REST, DTI, PET_FDG, EEG_19CH)',
    name            VARCHAR(128) NOT NULL
        COMMENT 'Human-readable modality name in Chinese',
    name_en         VARCHAR(128) DEFAULT NULL
        COMMENT 'Human-readable modality name in English',
    description     VARCHAR(512) DEFAULT NULL
        COMMENT 'Brief description of the modality',
    category        VARCHAR(64)  NOT NULL DEFAULT 'OTHER'
        COMMENT 'High-level category: MRI, CT, PET, SPECT, EEG, MEG, NIRS, OTHER',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1
        COMMENT 'Whether this modality is currently in use (1=active, 0=inactive)',
    sort_order      INT          NOT NULL DEFAULT 0
        COMMENT 'Display sort order',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modality_code (code),
    KEY idx_modality_category (category),
    KEY idx_modality_active (is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Imaging and electrophysiology modality code table';

-- ----------------------------------------------------------------------------
-- 2. scanner — physical scanning / recording devices
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scanner (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    name                        VARCHAR(128) NOT NULL
        COMMENT 'Scanner / device local name (e.g. Prisma-Fit 3T Bay 1)',
    manufacturer                VARCHAR(128) DEFAULT NULL
        COMMENT 'Manufacturer name (e.g. Siemens, Philips, GE, Nihon Kohden)',
    model                       VARCHAR(128) DEFAULT NULL
        COMMENT 'Model name or number (e.g. Prisma, Ingenia, Discovery MR750)',
    serial_number               VARCHAR(128) DEFAULT NULL
        COMMENT 'Manufacturer serial number',
    scanner_type                VARCHAR(64)  NOT NULL DEFAULT 'MRI'
        COMMENT 'Device type: MRI, CT, PET, SPECT, EEG, MEG, NIRS, PET_MR',
    facility_id                 BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> facility — hospital / site where device is located',
    department_id               BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> department — responsible department',
    location                    VARCHAR(256) DEFAULT NULL
        COMMENT 'Physical location within facility (building, floor, room)',
    is_active                   TINYINT(1)   NOT NULL DEFAULT 1
        COMMENT 'Whether this scanner is currently operational',
    calibration_date            DATE         DEFAULT NULL
        COMMENT 'Date of last calibration / QA',
    next_calibration_date       DATE         DEFAULT NULL
        COMMENT 'Next scheduled calibration / QA date',
    software_version            VARCHAR(64)  DEFAULT NULL
        COMMENT 'Current software / firmware version',
    magnetic_field_strength     DECIMAL(4,1) DEFAULT NULL
        COMMENT 'Magnetic field strength in Tesla (MRI only; e.g. 1.5, 3.0, 7.0)',
    number_of_channels          INT          DEFAULT NULL
        COMMENT 'Number of channels (EEG / MEG devices)',
    notes                       TEXT         DEFAULT NULL
        COMMENT 'Free-text notes about the device',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scanner_serial (serial_number),
    KEY idx_scanner_type (scanner_type),
    KEY idx_scanner_facility (facility_id),
    KEY idx_scanner_department (department_id),
    KEY idx_scanner_active (is_active),
    CONSTRAINT fk_scanner_facility FOREIGN KEY (facility_id)
        REFERENCES facility (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_scanner_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Scanner and recording device inventory';

-- ----------------------------------------------------------------------------
-- 3. imaging_session — a single visit of a subject to a scanner / device
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_session (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    patient_id              BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — the subject being scanned',
    scanner_id              BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> scanner — device used',
    imaging_order_id        BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> imaging_order — originating order (nullable for research scans)',
    session_date            DATE            NOT NULL
        COMMENT 'Date the imaging session took place',
    session_start_time      TIME            DEFAULT NULL
        COMMENT 'Time the session started',
    session_end_time        TIME            DEFAULT NULL
        COMMENT 'Time the session ended',
    session_status          VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULED'
        COMMENT 'Session status: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED',
    performing_technician   VARCHAR(128)    DEFAULT NULL
        COMMENT 'Name of the technologist / technician who performed the scan',
    referring_physician_id  BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> user — referring / ordering physician',
    sedation_used           TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether sedation / anesthesia was used',
    sedation_medication     VARCHAR(256)    DEFAULT NULL
        COMMENT 'Sedation medication name and dosage',
    sedation_notes          TEXT            DEFAULT NULL
        COMMENT 'Sedation-related notes',
    contrast_used           TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether contrast agent was administered',
    contrast_agent          VARCHAR(256)    DEFAULT NULL
        COMMENT 'Name of contrast agent (e.g. Gadobutrol, Iohexol)',
    contrast_dose           VARCHAR(64)     DEFAULT NULL
        COMMENT 'Contrast dose administered (e.g. 0.1 mmol/kg)',
    contrast_route          VARCHAR(64)     DEFAULT NULL
        COMMENT 'Route of contrast administration: IV, ORAL, RECTAL, INTRA_ARTERIAL',
    body_part_examined      VARCHAR(128)    DEFAULT NULL
        COMMENT 'Body part examined (e.g. HEAD, BRAIN, WHOLE_BODY)',
    study_instance_uid      VARCHAR(128)    DEFAULT NULL
        COMMENT 'DICOM Study Instance UID',
    study_description       VARCHAR(256)    DEFAULT NULL
        COMMENT 'DICOM study description',
    notes                   TEXT            DEFAULT NULL
        COMMENT 'Free-text notes about the session',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    KEY idx_imsession_patient (patient_id),
    KEY idx_imsession_scanner (scanner_id),
    KEY idx_imsession_order (imaging_order_id),
    KEY idx_imsession_date (session_date),
    KEY idx_imsession_status (session_status),
    KEY idx_imsession_physician (referring_physician_id),
    KEY idx_imsession_patient_date (patient_id, session_date),
    CONSTRAINT fk_imsession_patient FOREIGN KEY (patient_id)
        REFERENCES patient (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_imsession_scanner FOREIGN KEY (scanner_id)
        REFERENCES scanner (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_imsession_referring_physician FOREIGN KEY (referring_physician_id)
        REFERENCES user (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Imaging / recording session record — one visit per patient per device';

-- ----------------------------------------------------------------------------
-- 4. imaging_series — individual DICOM series within a session
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_series (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    imaging_session_id      BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> imaging_session',
    imaging_modality_id     BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> imaging_modality — the modality / protocol used',
    series_number           INT             DEFAULT NULL
        COMMENT 'DICOM series number within the study',
    series_description      VARCHAR(256)    DEFAULT NULL
        COMMENT 'DICOM series description (e.g. T1 MPRAGE sagittal)',
    series_uid              VARCHAR(128)    DEFAULT NULL
        COMMENT 'DICOM Series Instance UID (unique worldwide)',
    acquisition_date        DATE            DEFAULT NULL
        COMMENT 'Date of acquisition',
    acquisition_time        TIME            DEFAULT NULL
        COMMENT 'Time of acquisition',
    number_of_images        INT             DEFAULT 0
        COMMENT 'Total number of images / frames in this series',
    slice_thickness_mm      DECIMAL(5,2)    DEFAULT NULL
        COMMENT 'Slice thickness in millimeters',
    slice_spacing_mm        DECIMAL(5,2)    DEFAULT NULL
        COMMENT 'Spacing between slices in millimeters',
    pixel_spacing_x_mm      DECIMAL(6,4)    DEFAULT NULL
        COMMENT 'In-plane pixel spacing X in millimeters',
    pixel_spacing_y_mm      DECIMAL(6,4)    DEFAULT NULL
        COMMENT 'In-plane pixel spacing Y in millimeters',
    field_of_view_x_mm      DECIMAL(6,1)    DEFAULT NULL
        COMMENT 'Field of view X in millimeters',
    field_of_view_y_mm      DECIMAL(6,1)    DEFAULT NULL
        COMMENT 'Field of view Y in millimeters',
    matrix_size_rows        INT             DEFAULT NULL
        COMMENT 'Image matrix rows (e.g. 256)',
    matrix_size_cols        INT             DEFAULT NULL
        COMMENT 'Image matrix columns (e.g. 256)',
    reconstruction_kernel   VARCHAR(128)    DEFAULT NULL
        COMMENT 'Reconstruction kernel / filter name',
    protocol_name           VARCHAR(256)    DEFAULT NULL
        COMMENT 'Acquisition protocol name',
    anatomical_region       VARCHAR(64)     DEFAULT NULL
        COMMENT 'Anatomical region: BRAIN, HEAD_NECK, SPINE, CHEST, ABDOMEN, PELVIS, LIMB, WHOLE_BODY',
    laterality              VARCHAR(16)     DEFAULT NULL
        COMMENT 'Laterality: LEFT, RIGHT, BILATERAL, MIDLINE',
    repetition_time_ms      DECIMAL(10,3)   DEFAULT NULL
        COMMENT 'Repetition time (TR) in milliseconds',
    echo_time_ms            DECIMAL(10,3)   DEFAULT NULL
        COMMENT 'Echo time (TE) in milliseconds',
    inversion_time_ms       DECIMAL(10,3)   DEFAULT NULL
        COMMENT 'Inversion time (TI) in milliseconds',
    flip_angle_deg          DECIMAL(5,1)    DEFAULT NULL
        COMMENT 'Flip angle in degrees',
    magnetic_field_strength DECIMAL(4,1)    DEFAULT NULL
        COMMENT 'Magnetic field strength in Tesla (extracted from DICOM)',
    series_file_path        VARCHAR(1024)   DEFAULT NULL
        COMMENT 'Filesystem path to the series data directory',
    series_format           VARCHAR(32)     NOT NULL DEFAULT 'DICOM'
        COMMENT 'Data format: DICOM, NIFTI, ANALYZE, MINC, PNG, JSON',
    file_size_bytes         BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'Total file size in bytes for this series',
    is_derived              TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether this is a derived / processed series (0=raw, 1=derived)',
    notes                   TEXT            DEFAULT NULL
        COMMENT 'Free-text notes about this series',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_series_uid (series_uid),
    KEY idx_iseries_session (imaging_session_id),
    KEY idx_iseries_modality (imaging_modality_id),
    KEY idx_iseries_date (acquisition_date),
    KEY idx_iseries_region (anatomical_region),
    KEY idx_iseries_format (series_format),
    KEY idx_iseries_derived (is_derived),
    KEY idx_iseries_session_number (imaging_session_id, series_number),
    CONSTRAINT fk_iseries_session FOREIGN KEY (imaging_session_id)
        REFERENCES imaging_session (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_iseries_modality FOREIGN KEY (imaging_modality_id)
        REFERENCES imaging_modality (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Imaging series — individual acquisition series within a session';

-- ----------------------------------------------------------------------------
-- 5. eeg_recording — EEG / electrophysiology recording details
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eeg_recording (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    imaging_session_id      BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> imaging_session — parent session',
    recording_date          DATE            NOT NULL
        COMMENT 'Date of EEG recording',
    recording_start_time    TIME            DEFAULT NULL
        COMMENT 'Recording start time',
    recording_duration_sec  INT             DEFAULT NULL
        COMMENT 'Total recording duration in seconds',
    sampling_rate_hz        INT             DEFAULT NULL
        COMMENT 'Sampling rate in Hz (e.g. 250, 500, 1000)',
    number_of_channels      INT             DEFAULT NULL
        COMMENT 'Number of recording channels',
    electrode_montage       VARCHAR(128)    DEFAULT NULL
        COMMENT 'Electrode montage used: 10_20, 10_10, 10_05, CUSTOM',
    reference_electrode     VARCHAR(64)     DEFAULT NULL
        COMMENT 'Reference electrode placement (e.g. Cz, average, linked_mastoid)',
    ground_electrode        VARCHAR(64)     DEFAULT NULL
        COMMENT 'Ground electrode placement',
    online_filter_low_cut   DECIMAL(4,1)    DEFAULT NULL
        COMMENT 'Online low-cut / high-pass filter (Hz)',
    online_filter_high_cut  DECIMAL(5,1)    DEFAULT NULL
        COMMENT 'Online high-cut / low-pass filter (Hz)',
    notch_filter_hz         DECIMAL(4,1)    DEFAULT NULL
        COMMENT 'Notch filter frequency in Hz (e.g. 50.0, 60.0)',
    impedance_check_result  VARCHAR(256)    DEFAULT NULL
        COMMENT 'Impedance check results summary',
    max_impedance_kohm      DECIMAL(5,1)    DEFAULT NULL
        COMMENT 'Maximum electrode impedance in kOhm',
    recording_conditions    VARCHAR(256)    DEFAULT NULL
        COMMENT 'Comma-separated conditions: EYES_OPEN, EYES_CLOSED, HYPERVENTILATION, PHOTIC_STIMULATION, SLEEP_DEPRIVED, RESTING_STATE, TASK, SLEEP',
    task_description        VARCHAR(512)    DEFAULT NULL
        COMMENT 'Task description if task condition was used (e.g. oddball, n_back, stroop)',
    eeg_file_path           VARCHAR(1024)   DEFAULT NULL
        COMMENT 'Filesystem path to the EEG data file',
    eeg_file_format         VARCHAR(32)     DEFAULT NULL
        COMMENT 'EEG file format: EDF, BDF, BrainVision, FIF, SET, MEF3, XDF',
    file_size_bytes         BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'Total EEG file size in bytes',
    artifact_removal_method VARCHAR(256)    DEFAULT NULL
        COMMENT 'Artifact removal / correction method if preprocessed',
    artifact_notes          TEXT            DEFAULT NULL
        COMMENT 'Notes about artifacts, bad channels, or data quality',
    notes                   TEXT            DEFAULT NULL
        COMMENT 'Free-text notes about the recording',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    KEY idx_eeg_session (imaging_session_id),
    KEY idx_eeg_date (recording_date),
    KEY idx_eeg_format (eeg_file_format),
    KEY idx_eeg_channels (number_of_channels),
    CONSTRAINT fk_eeg_session FOREIGN KEY (imaging_session_id)
        REFERENCES imaging_session (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='EEG / electrophysiology recording details';

-- ----------------------------------------------------------------------------
-- 6. imaging_report — radiologist / clinician readout report
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_report (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    imaging_session_id          BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> imaging_session — the session being reported',
    reporting_radiologist_id    BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> user — radiologist / clinician authoring the report',
    report_date                 DATETIME        NOT NULL
        COMMENT 'Date and time the report was created',
    report_status               VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'
        COMMENT 'Report status: DRAFT, PRELIMINARY, FINAL, AMENDED, ADDENDUM, CANCELLED',
    report_type                 VARCHAR(64)     DEFAULT NULL
        COMMENT 'Type of report: CLINICAL, RESEARCH, SECOND_OPINION, SCREENING',
    clinical_indication         TEXT            DEFAULT NULL
        COMMENT 'Clinical indication / reason for the imaging exam',
    technique                   TEXT            DEFAULT NULL
        COMMENT 'Imaging technique / protocol description for the report',
    comparison_studies          TEXT            DEFAULT NULL
        COMMENT 'Description of comparison / prior studies reviewed',
    findings                    TEXT            DEFAULT NULL
        COMMENT 'Detailed imaging findings',
    impression                  TEXT            DEFAULT NULL
        COMMENT 'Impression / conclusion section',
    recommendations             TEXT            DEFAULT NULL
        COMMENT 'Recommendations for further workup or follow-up',
    structured_report_json      JSON            DEFAULT NULL
        COMMENT 'Structured / coded report in JSON format (e.g. standardized brain atrophy scores)',
    report_template_id          BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> report_template if using a structured template',
    is_urgent                   TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether this report is marked as urgent',
    is_critical_finding         TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether a critical / unexpected finding was identified',
    critical_finding_communicated TINYINT(1)    NOT NULL DEFAULT 0
        COMMENT 'Whether the critical finding was communicated to the referring clinician',
    communicated_to             VARCHAR(128)    DEFAULT NULL
        COMMENT 'Name of the person the critical finding was communicated to',
    communicated_date           DATETIME        DEFAULT NULL
        COMMENT 'Date and time critical finding was communicated',
    signed_by                   BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> user — user who signed / finalized the report',
    signed_date                 DATETIME        DEFAULT NULL
        COMMENT 'Date and time the report was signed',
    report_number               VARCHAR(64)     DEFAULT NULL
        COMMENT 'Institution / system report accession number',
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_number (report_number),
    KEY idx_ireport_session (imaging_session_id),
    KEY idx_ireport_radiologist (reporting_radiologist_id),
    KEY idx_ireport_date (report_date),
    KEY idx_ireport_status (report_status),
    KEY idx_ireport_urgent (is_urgent),
    KEY idx_ireport_critical (is_critical_finding),
    KEY idx_ireport_signed_by (signed_by),
    KEY idx_ireport_session_status (imaging_session_id, report_status),
    CONSTRAINT fk_ireport_session FOREIGN KEY (imaging_session_id)
        REFERENCES imaging_session (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ireport_radiologist FOREIGN KEY (reporting_radiologist_id)
        REFERENCES user (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_ireport_signed_by FOREIGN KEY (signed_by)
        REFERENCES user (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Imaging / electrophysiology readout report';

-- ----------------------------------------------------------------------------
-- 7. imaging_order — exam request / order placed by clinician
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_order (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    patient_id              BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — the subject for whom imaging is ordered',
    ordering_physician_id   BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> user — clinician placing the order',
    order_date              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Date and time the order was placed',
    order_status            VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'
        COMMENT 'Order status: DRAFT, ORDERED, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, REJECTED',
    order_type              VARCHAR(64)     DEFAULT NULL
        COMMENT 'Order type: CLINICAL, RESEARCH, SCREENING, FOLLOW_UP, URGENT',
    imaging_modality_id     BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> imaging_modality — requested modality',
    clinical_indication     TEXT            DEFAULT NULL
        COMMENT 'Clinical indication / reason for the imaging exam',
    urgency                 VARCHAR(16)     NOT NULL DEFAULT 'ROUTINE'
        COMMENT 'Urgency level: ROUTINE, URGENT, STAT',
    desired_date            DATE            DEFAULT NULL
        COMMENT 'Desired exam date',
    order_notes             TEXT            DEFAULT NULL
        COMMENT 'Additional order notes or instructions',
    diagnosis_codes         VARCHAR(1024)   DEFAULT NULL
        COMMENT 'Comma-separated ICD-10 diagnosis codes associated with the order (e.g. G30.9, F32.2)',
    is_contrast_requested   TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether contrast administration is requested',
    contrast_type           VARCHAR(128)    DEFAULT NULL
        COMMENT 'Requested contrast type if applicable',
    special_instructions    TEXT            DEFAULT NULL
        COMMENT 'Special instructions for the technologist (e.g. patient positioning, specific sequences)',
    external_order_id       VARCHAR(128)    DEFAULT NULL
        COMMENT 'External system order ID for HIS/RIS integration',
    external_facility       VARCHAR(256)    DEFAULT NULL
        COMMENT 'External facility name if ordered from outside',
    is_research             TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether this is a research-only order (not clinical)',
    research_protocol_id    BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> research_protocol if applicable',
    approved_by             BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'FK -> user — person who approved the order (e.g. radiologist, PI)',
    approved_date           DATETIME        DEFAULT NULL
        COMMENT 'Date and time the order was approved',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Record creation timestamp',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Last update timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_order_id (external_order_id),
    KEY idx_iorder_patient (patient_id),
    KEY idx_iorder_physician (ordering_physician_id),
    KEY idx_iorder_date (order_date),
    KEY idx_iorder_status (order_status),
    KEY idx_iorder_urgency (urgency),
    KEY idx_iorder_modality (imaging_modality_id),
    KEY idx_iorder_desired_date (desired_date),
    KEY idx_iorder_patient_status (patient_id, order_status),
    CONSTRAINT fk_iorder_patient FOREIGN KEY (patient_id)
        REFERENCES patient (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_iorder_physician FOREIGN KEY (ordering_physician_id)
        REFERENCES user (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_iorder_modality FOREIGN KEY (imaging_modality_id)
        REFERENCES imaging_modality (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_iorder_approved_by FOREIGN KEY (approved_by)
        REFERENCES user (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Imaging exam order / request placed by clinician';

ALTER TABLE imaging_session
    ADD CONSTRAINT fk_imsession_order FOREIGN KEY (imaging_order_id)
        REFERENCES imaging_order (id) ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- END OF V004__imaging.sql
-- ============================================================================
