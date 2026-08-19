-- ============================================================================
-- V009__export_adni.sql
-- Export & ADNI: export_request, download_approval, adni_dataset
--
-- Description:
--   Data export request lifecycle management, multi-level download approval
--   workflow, and ADNI (Alzheimer's Disease Neuroimaging Initiative) dataset
--   integration for the Brain Health Platform.
--
-- Tables:
--   1. export_request      - Core export request tracking
--   2. download_approval   - Multi-level approval workflow
--   3. adni_dataset        - ADNI subject/visit data repository
--
-- Prerequisites:
--   V001-V008 must have been applied (user, project, imaging_modality, etc.)
-- ============================================================================

-- ============================================================================
-- 1. export_request
--    Manages the complete lifecycle of a data export request: submission,
--    approval, processing, file generation, and secure download.
-- ============================================================================
CREATE TABLE IF NOT EXISTS `export_request` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                        COMMENT 'Primary key',

    -- Identification ----------------------------------------------------------
    `request_number`    VARCHAR(64) NOT NULL
                        COMMENT 'Auto-generated tracking number, format EXP-YYYYMMDD-NNNN',

    -- Requester & context -----------------------------------------------------
    `requester_id`      BIGINT UNSIGNED NOT NULL
                        COMMENT 'FK: user.id - the user who submitted this export request',
    `project_id`        BIGINT UNSIGNED NULL
                        COMMENT 'FK: project.id - associated research project (nullable for ad-hoc exports)',

    -- Export specification ----------------------------------------------------
    `export_type`       VARCHAR(64) NOT NULL
                        COMMENT 'Category of data to export: clinical_data, imaging_data, genomic_data, lab_data, demographic_data, adni_dataset, neuropsych_data, full_export, custom',
    `data_scope`        JSON NULL
                        COMMENT 'Detailed scope specification (JSON): subject_ids, date_range, variable_list, modality_filter, diagnosis_filter, anonymization_rules',
    `export_format`     VARCHAR(32) NOT NULL DEFAULT 'CSV'
                        COMMENT 'Output format: CSV, Excel, SPSS, SAS, JSON, DICOM, NIfTI, TSV',
    `anonymization`     VARCHAR(32) NOT NULL DEFAULT 'full'
                        COMMENT 'De-identification level: none, pseudo, k_anonymized, full, custom',
    `include_phi`       TINYINT(1) NOT NULL DEFAULT 0
                        COMMENT 'Whether protected health information is included: 0=no, 1=yes',

    -- Lifecycle status --------------------------------------------------------
    `status`            VARCHAR(32) NOT NULL DEFAULT 'draft'
                        COMMENT 'Status: draft, pending_review, under_review, approved, rejected, processing, completed, failed, expired, cancelled',
    `reason`            TEXT NULL
                        COMMENT 'Scientific or clinical justification for the export request',
    `rejection_reason`  TEXT NULL
                        COMMENT 'Reason for rejection when status=rejected',
    `failure_message`   TEXT NULL
                        COMMENT 'Error details when status=failed',

    -- Approval ----------------------------------------------------------------
    `approved_by`       BIGINT UNSIGNED NULL
                        COMMENT 'FK: user.id - user who gave final approval',
    `approved_at`       DATETIME NULL
                        COMMENT 'Timestamp of final approval',

    -- Processing --------------------------------------------------------------
    `processing_at`     DATETIME NULL
                        COMMENT 'Timestamp when export processing began',
    `completed_at`      DATETIME NULL
                        COMMENT 'Timestamp when export file was generated successfully',

    -- Output ------------------------------------------------------------------
    `file_path`         VARCHAR(512) NULL
                        COMMENT 'Absolute or relative path to the generated export file on storage',
    `file_name`         VARCHAR(256) NULL
                        COMMENT 'Original file name presented to the downloader',
    `file_size`         BIGINT UNSIGNED NULL
                        COMMENT 'Export file size in bytes',
    `record_count`      INT UNSIGNED NULL
                        COMMENT 'Total number of data records included in the export',
    `checksum`          VARCHAR(128) NULL
                        COMMENT 'SHA-256 checksum of the export file for integrity verification',

    -- Download control --------------------------------------------------------
    `expires_at`        DATETIME NULL
                        COMMENT 'Download link expiration (NULL = never expires)',
    `download_count`    INT UNSIGNED NOT NULL DEFAULT 0
                        COMMENT 'Cumulative download count for the generated file',
    `max_downloads`     INT UNSIGNED NULL
                        COMMENT 'Maximum allowed downloads (NULL = unlimited)',
    `last_downloaded_at` DATETIME NULL
                        COMMENT 'Timestamp of the most recent download',

    -- Soft delete -------------------------------------------------------------
    `is_deleted`        TINYINT(1) NOT NULL DEFAULT 0
                        COMMENT 'Soft delete flag: 0=active, 1=deleted',
    `deleted_at`        DATETIME NULL
                        COMMENT 'Soft delete timestamp',

    -- Audit -------------------------------------------------------------------
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        COMMENT 'Record creation timestamp',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        COMMENT 'Record last update timestamp',

    -- Constraints -------------------------------------------------------------
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_export_request_number` (`request_number`),

    -- Single-column indexes
    INDEX `idx_requester_id`        (`requester_id`),
    INDEX `idx_project_id`          (`project_id`),
    INDEX `idx_status`              (`status`),
    INDEX `idx_export_type`         (`export_type`),
    INDEX `idx_export_format`       (`export_format`),
    INDEX `idx_approved_by`         (`approved_by`),
    INDEX `idx_created_at`          (`created_at`),
    INDEX `idx_completed_at`        (`completed_at`),
    INDEX `idx_expires_at`          (`expires_at`),
    INDEX `idx_is_deleted`          (`is_deleted`),

    -- Composite indexes for common query patterns
    INDEX `idx_requester_status`    (`requester_id`, `status`),
    INDEX `idx_project_status`      (`project_id`, `status`),
    INDEX `idx_status_created`      (`status`, `created_at`),
    INDEX `idx_type_status`         (`export_type`, `status`),

    -- Foreign keys
    CONSTRAINT `fk_export_request_requester`
        FOREIGN KEY (`requester_id`) REFERENCES `user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_export_request_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_export_request_approver`
        FOREIGN KEY (`approved_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Data export request lifecycle - submission, approval, processing, file generation, and download tracking';


-- ============================================================================
-- 2. download_approval
--    Multi-level approval chain for export/download requests. Each row
--    represents one approver at one approval level. An export request may
--    require one or more approval levels (e.g. PI -> Ethics -> Admin).
-- ============================================================================
CREATE TABLE IF NOT EXISTS `download_approval` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                            COMMENT 'Primary key',

    -- Parent request ----------------------------------------------------------
    `export_request_id`     BIGINT UNSIGNED NOT NULL
                            COMMENT 'FK: export_request.id - the export request being approved',

    -- Approver ----------------------------------------------------------------
    `approver_id`           BIGINT UNSIGNED NOT NULL
                            COMMENT 'FK: user.id - the approver assigned to this step',
    `approval_level`        INT UNSIGNED NOT NULL DEFAULT 1
                            COMMENT 'Sequential approval level: 1=PI/first-level, 2=ethics/second-level, 3=data-custodian, 4=admin/final',
    `approval_level_label`  VARCHAR(64) NULL
                            COMMENT 'Human-readable label for this level: PI Review, Ethics Committee, Data Custodian, Administrator',
    `approval_role`         VARCHAR(64) NULL
                            COMMENT 'Role code required: pi, ethics_officer, data_custodian, admin',

    -- Decision ----------------------------------------------------------------
    `status`                VARCHAR(32) NOT NULL DEFAULT 'pending'
                            COMMENT 'Decision: pending, approved, rejected, skipped, delegated, recused',
    `comments`              TEXT NULL
                            COMMENT 'Approver notes, conditions, or reasons for rejection',
    `decided_at`            DATETIME NULL
                            COMMENT 'Timestamp when the approval decision was made',

    -- Delegation (optional) ---------------------------------------------------
    `delegated_to`          BIGINT UNSIGNED NULL
                            COMMENT 'FK: user.id - if the approver delegated this step to another user',
    `delegation_reason`     VARCHAR(256) NULL
                            COMMENT 'Reason for delegation',

    -- Notification tracking ---------------------------------------------------
    `notification_sent`     TINYINT(1) NOT NULL DEFAULT 0
                            COMMENT 'Whether initial approval notification was sent',
    `notification_sent_at`  DATETIME NULL
                            COMMENT 'Timestamp when notification was sent',
    `reminder_count`        INT UNSIGNED NOT NULL DEFAULT 0
                            COMMENT 'Number of reminder notifications sent',
    `last_reminder_at`      DATETIME NULL
                            COMMENT 'Timestamp of the most recent reminder',

    -- Audit -------------------------------------------------------------------
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                            COMMENT 'Record creation timestamp',
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                            COMMENT 'Record last update timestamp',

    -- Constraints -------------------------------------------------------------
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_approver_level` (`export_request_id`, `approver_id`, `approval_level`),

    -- Single-column indexes
    INDEX `idx_export_request_id`   (`export_request_id`),
    INDEX `idx_approver_id`         (`approver_id`),
    INDEX `idx_status`              (`status`),
    INDEX `idx_approval_level`      (`approval_level`),
    INDEX `idx_delegated_to`        (`delegated_to`),

    -- Composite indexes
    INDEX `idx_request_status`      (`export_request_id`, `status`),
    INDEX `idx_approver_status`     (`approver_id`, `status`),
    INDEX `idx_level_status`        (`approval_level`, `status`),

    -- Foreign keys
    CONSTRAINT `fk_download_approval_request`
        FOREIGN KEY (`export_request_id`) REFERENCES `export_request` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_download_approval_approver`
        FOREIGN KEY (`approver_id`) REFERENCES `user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_download_approval_delegated`
        FOREIGN KEY (`delegated_to`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Multi-level export/download approval workflow - one row per approval step per request';


-- ============================================================================
-- 3. adni_dataset
--    Repository for imported ADNI (Alzheimer's Disease Neuroimaging Initiative)
--    data. Each row represents one subject at one visit within one study phase.
--    Clinical, biomarker, imaging, and genetic data are stored as structured
--    JSON for flexibility across different ADNI data dictionaries.
--
--    ADNI phases: ADNI1 (2004-2009), ADNIGO (2009-2011), ADNI2 (2011-2016),
--    ADNI3 (2016-2022), ADNI4 (2022-present).
-- ============================================================================
CREATE TABLE IF NOT EXISTS `adni_dataset` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                            COMMENT 'Primary key',

    -- ADNI core identifiers ---------------------------------------------------
    `adni_subject_id`       VARCHAR(64) NOT NULL
                            COMMENT 'ADNI subject ID (e.g., 002_S_0413, 003_S_4281)',
    `adni_visit_code`       VARCHAR(32) NULL
                            COMMENT 'ADNI visit code: bl=baseline, m03=month3, m06=month6, m12=month12, m18=month18, m24=month24, m36=month36, m48=month48, m60=month60, m72=month72, m84=month84, m96=month96, m108=month108, m120=month120, m132=month132, m144=month144, m156=month156, m168=month168, m180=month180, m192=month192, uns1=unscheduled',
    `study_phase`           VARCHAR(32) NULL
                            COMMENT 'ADNI study phase: ADNI1, ADNIGO, ADNI2, ADNI3, ADNI4',
    `site_id`               VARCHAR(32) NULL
                            COMMENT 'ADNI recruiting site identifier',
    `rid`                   VARCHAR(32) NULL
                            COMMENT 'ADNI roster ID - unique per subject across all phases',

    -- Imaging reference -------------------------------------------------------
    `imaging_modality_id`   BIGINT UNSIGNED NULL
                            COMMENT 'FK: imaging_modality.id - primary imaging modality for this visit',

    -- Diagnosis ---------------------------------------------------------------
    `diagnosis_group`       VARCHAR(64) NULL
                            COMMENT 'Baseline/current diagnostic category: CN=Cognitively Normal, SMC=Subjective Memory Concern, EMCI=Early MCI, LMCI=Late MCI, MCI=Mild Cognitive Impairment, AD=Alzheimer Disease, N=Normal, Other',
    `diagnosis_change`      VARCHAR(64) NULL
                            COMMENT 'Longitudinal diagnosis change: stable=MCI/diagnosis unchanged, conv=MCI to AD conversion, rev=MCI to CN reversion, other',
    `diagnosis_change_from` VARCHAR(64) NULL
                            COMMENT 'Diagnosis before conversion (CN, MCI, etc.)',
    `diagnosis_change_to`   VARCHAR(64) NULL
                            COMMENT 'Diagnosis after conversion (MCI, AD, CN, etc.)',

    -- Demographics (GB/T standards where applicable) --------------------------
    `age_at_baseline`       DECIMAL(5,2) NULL
                            COMMENT 'Age in years at baseline visit',
    `age_at_visit`          DECIMAL(5,2) NULL
                            COMMENT 'Age in years at this specific visit',
    `sex`                   VARCHAR(8) NULL
                            COMMENT 'Biological sex per GB/T 2261.1: 1=male, 2=female',
    `education_years`       DECIMAL(4,1) NULL
                            COMMENT 'Total years of formal education',
    `ethnicity`             VARCHAR(32) NULL
                            COMMENT 'Ethnicity per GB/T 3304: 01=Han, 02=Mongolian, 03=Hui, 04=Tibetan, ... (Chinese standard); also supports ADNI ethnicity codes',
    `race`                  VARCHAR(64) NULL
                            COMMENT 'Race per ADNI standard: White, Black/African American, Asian, American Indian/Alaskan Native, Native Hawaiian/Pacific Islander, More than one, Unknown',
    `marital_status`        VARCHAR(32) NULL
                            COMMENT 'Marital status: 10=unmarried, 20=married, 30=widowed, 40=divorced, 90=unknown',
    `handedness`            VARCHAR(16) NULL
                            COMMENT 'Dominant hand: right, left, ambidextrous',

    -- APOE genotype (critical Alzheimer biomarker) ----------------------------
    `apoe_genotype`         VARCHAR(16) NULL
                            COMMENT 'APOE genotype (e.g., e2/e2, e2/e3, e2/e4, e3/e3, e3/e4, e4/e4)',
    `apoe_e2_count`         TINYINT UNSIGNED NULL
                            COMMENT 'Number of APOE epsilon-2 alleles: 0, 1, 2',
    `apoe_e4_count`         TINYINT UNSIGNED NULL
                            COMMENT 'Number of APOE epsilon-4 alleles (Alzheimer risk): 0, 1, 2',

    -- Structured data payloads (JSON) -----------------------------------------
    `demographic_data`      JSON NULL
                            COMMENT 'Extended demographic data: height_cm, weight_kg, bmi, blood_pressure, pulse, living_situation, caregiver_info',
    `clinical_data`         JSON NULL
                            COMMENT 'Clinical assessments: MMSE, MoCA, CDR (global + sum of boxes), ADAS-Cog (11/13), FAQ, GDS, NPI, NPI-Q, Hachinski, Functional Assessment Questionnaire',
    `neuropsych_data`       JSON NULL
                            COMMENT 'Neuropsychological test battery: RAVLT, Logical Memory, Trails A/B, Category Fluency, BNTT, Clock Drawing, Digit Span, Digit Symbol',
    `imaging_metadata`      JSON NULL
                            COMMENT 'Imaging parameters: scanner_manufacturer, scanner_model, field_strength, coil, acquisition_protocol, voxel_size, matrix_size, slice_count, DICOM_series_uid',
    `biomarker_data`        JSON NULL
                            COMMENT 'CSF and blood biomarkers: Abeta42_csf, Abeta40_csf, Abeta42_40_ratio, p_tau181_csf, t_tau_csf, p_tau181_t_tau_ratio, p_tau181_Abeta42_ratio, NfL_csf, NfL_plasma, GFAP_plasma, Abeta42_40_plasma, p_tau217_plasma, p_tau181_plasma',
    `genetic_data`          JSON NULL
                            COMMENT 'Genetics beyond APOE: GWAS summary, polygenic_risk_score, APP/PSEN1/PSEN2 variants, TREM2, SORL1, TOMM40, BDNF, CLU, CR1, PICALM, BIN1, CD33, MS4A, ABCA7, CD2AP, EPHA1, HLA-DRB, INPP5D, MEF2C, NME8, PTK2B',
    `medication_data`       JSON NULL
                            COMMENT 'Concomitant medications: cholinesterase_inhibitors (donepezil, rivastigmine, galantamine), memantine, antidepressants, antipsychotics, anxiolytics, antihypertensives, statins, antidiabetics',
    `medical_history`       JSON NULL
                            COMMENT 'Comorbidities and history: hypertension, diabetes, hyperlipidemia, CVD, stroke, TBI, depression, anxiety, thyroid, smoking, alcohol, family_history_AD',

    -- Source traceability -----------------------------------------------------
    `file_location`         VARCHAR(512) NULL
                            COMMENT 'Path to original ADNI data file/directory on storage',
    `imaging_file_path`     VARCHAR(512) NULL
                            COMMENT 'Path to imaging files (MRI T1, MRI T2, fMRI, DTI, ASL, PET-FDG, PET-Amyloid, PET-Tau)',
    `source_table`          VARCHAR(64) NULL
                            COMMENT 'Original ADNI merged table name: ADNIMERGE, MRI_3T, MRI_1.5T, UPENNBIOMK, UPENNBIOMK_MASTER, UWNPSYCHSUM, ECSEGSURF, UCSFFSL, UCSFFSX, UCBERKELEYAV45, UCBERKELEYAV1451, BAPETANALYSIS',
    `source_file`           VARCHAR(256) NULL
                            COMMENT 'Original ADNI CSV/data file name on disk',

    -- Import & validation -----------------------------------------------------
    `is_imported`           TINYINT(1) NOT NULL DEFAULT 0
                            COMMENT 'Whether raw data has been parsed and stored: 0=no, 1=yes',
    `imported_by`           BIGINT UNSIGNED NULL
                            COMMENT 'FK: user.id - who performed the import',
    `imported_at`           DATETIME NULL
                            COMMENT 'Timestamp of data import',
    `is_validated`          TINYINT(1) NOT NULL DEFAULT 0
                            COMMENT 'Whether data passed QC and validation checks',
    `validated_by`          BIGINT UNSIGNED NULL
                            COMMENT 'FK: user.id - who validated the data',
    `validated_at`          DATETIME NULL
                            COMMENT 'Timestamp of validation',
    `validation_notes`      TEXT NULL
                            COMMENT 'QC findings, data issues, outlier flags',

    -- Soft delete -------------------------------------------------------------
    `is_deleted`            TINYINT(1) NOT NULL DEFAULT 0
                            COMMENT 'Soft delete flag: 0=active, 1=deleted',
    `deleted_at`            DATETIME NULL
                            COMMENT 'Soft delete timestamp',

    -- Audit -------------------------------------------------------------------
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                            COMMENT 'Record creation timestamp',
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                            COMMENT 'Record last update timestamp',

    -- Constraints -------------------------------------------------------------
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_adni_subject_visit_phase` (`adni_subject_id`, `adni_visit_code`, `study_phase`),

    -- Single-column indexes for common filter/join patterns
    INDEX `idx_adni_subject_id`         (`adni_subject_id`),
    INDEX `idx_rid`                     (`rid`),
    INDEX `idx_study_phase`             (`study_phase`),
    INDEX `idx_site_id`                 (`site_id`),
    INDEX `idx_diagnosis_group`         (`diagnosis_group`),
    INDEX `idx_diagnosis_change`        (`diagnosis_change`),
    INDEX `idx_imaging_modality_id`     (`imaging_modality_id`),
    INDEX `idx_sex`                     (`sex`),
    INDEX `idx_apoe_genotype`           (`apoe_genotype`),
    INDEX `idx_apoe_e4_count`           (`apoe_e4_count`),
    INDEX `idx_age_at_baseline`         (`age_at_baseline`),
    INDEX `idx_is_imported`             (`is_imported`),
    INDEX `idx_is_validated`            (`is_validated`),
    INDEX `idx_imported_at`             (`imported_at`),

    -- Composite indexes for common analytical queries
    INDEX `idx_dx_sex`                  (`diagnosis_group`, `sex`),
    INDEX `idx_dx_apoe`                 (`diagnosis_group`, `apoe_genotype`),
    INDEX `idx_dx_phase`                (`diagnosis_group`, `study_phase`),
    INDEX `idx_phase_dx`                (`study_phase`, `diagnosis_group`),
    INDEX `idx_subject_visit`           (`adni_subject_id`, `adni_visit_code`),
    INDEX `idx_dx_e4`                   (`diagnosis_group`, `apoe_e4_count`),

    -- Foreign keys
    CONSTRAINT `fk_adni_dataset_imaging_modality`
        FOREIGN KEY (`imaging_modality_id`) REFERENCES `imaging_modality` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_adni_dataset_imported_by`
        FOREIGN KEY (`imported_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_adni_dataset_validated_by`
        FOREIGN KEY (`validated_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ADNI (Alzheimer Disease Neuroimaging Initiative) dataset - one row per subject per visit per study phase, with structured clinical, biomarker, imaging, and genetic data';
