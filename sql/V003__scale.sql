-- ============================================================
-- V003: Scale Assessment Module
-- Description: Psychological/neurological scale instruments,
--   examiners, assessment records, and item-level data.
-- Requires: V001 (core/user), V002 (subject)
-- Standards: GB/T 2261.1 (sex), GB/T 3304 (ethnicity),
--   GB/T 4658 (education), GB/T 2659 (nationality)
-- ============================================================

-- ------------------------------------------------------------
-- 1. scale_instrument: Scale / questionnaire instrument master
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_instrument` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `name`                 VARCHAR(200)    NOT NULL
        COMMENT 'Full instrument name',
    `abbreviation`         VARCHAR(50)     NOT NULL
        COMMENT 'Common abbreviation, e.g. MMSE, MoCA, HAMD',
    `version`              VARCHAR(50)     NOT NULL DEFAULT '1.0'
        COMMENT 'Instrument version',
    `category`             VARCHAR(50)     NOT NULL
        COMMENT 'Category: COGNITIVE, MOOD, ANXIETY, BEHAVIORAL, FUNCTIONAL, NEUROPSYCH, SLEEP, PAIN, QOL, OTHER',
    `subcategory`          VARCHAR(100)         NULL
        COMMENT 'Finer-grained subcategory',
    `description`          TEXT                NULL
        COMMENT 'Full description and purpose of the instrument',
    `total_items`          INT UNSIGNED    NOT NULL DEFAULT 0
        COMMENT 'Total number of items/questions',
    `scoring_method`       VARCHAR(50)     NOT NULL DEFAULT 'SUM'
        COMMENT 'Scoring method: SUM, WEIGHTED, NORMED, CUTOFF, FORMULA',
    `min_score`            DECIMAL(10,2)       NULL
        COMMENT 'Minimum possible total score',
    `max_score`            DECIMAL(10,2)       NULL
        COMMENT 'Maximum possible total score',
    `cutoff_score`         DECIMAL(10,2)       NULL
        COMMENT 'Clinical cutoff threshold (equal or above indicates impairment)',
    `administration_time_min` INT UNSIGNED     NULL
        COMMENT 'Typical administration time in minutes',
    `language`             VARCHAR(20)     NOT NULL DEFAULT 'zh-CN'
        COMMENT 'Primary language code (BCP 47)',
    `instructions`         TEXT                NULL
        COMMENT 'Administration and scoring instructions',
    `reference`            VARCHAR(500)        NULL
        COMMENT 'Primary publication / validation reference',
    `copyright_info`       VARCHAR(500)        NULL
        COMMENT 'Copyright / licensing information',
    `is_active`            TINYINT(1)      NOT NULL DEFAULT 1
        COMMENT 'Whether instrument is active (1=active, 0=inactive)',
    `status`               VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'Lifecycle status: ACTIVE, DEPRECATED, RETIRED, DRAFT',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_instrument_abbr_version` (`abbreviation`, `version`),
    KEY `idx_instrument_category` (`category`),
    KEY `idx_instrument_is_active` (`is_active`),
    KEY `idx_instrument_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Scale instrument master: MMSE, MoCA, HAMD, HAMA, CDR, NPI, ADL, FAQ, GDS, PHQ-9, etc.';

-- ------------------------------------------------------------
-- 2. scale_domain: Domain/subscale within an instrument
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_domain` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument',
    `name`                 VARCHAR(200)    NOT NULL
        COMMENT 'Domain name, e.g. Orientation, Attention, Delayed Recall',
    `abbreviation`         VARCHAR(50)         NULL
        COMMENT 'Short code for the domain',
    `description`          VARCHAR(1000)       NULL
        COMMENT 'What this domain measures',
    `item_start`           INT UNSIGNED    NOT NULL DEFAULT 1
        COMMENT 'First item number belonging to this domain',
    `item_end`             INT UNSIGNED    NOT NULL DEFAULT 1
        COMMENT 'Last item number belonging to this domain',
    `max_domain_score`     DECIMAL(10,2)       NULL
        COMMENT 'Maximum score for this domain alone',
    `sort_order`           INT UNSIGNED    NOT NULL DEFAULT 0
        COMMENT 'Display order',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_domain_instrument_id` (`instrument_id`),
    KEY `idx_domain_sort_order` (`sort_order`),
    CONSTRAINT `fk_domain_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Domains / subscales within each instrument';

-- ------------------------------------------------------------
-- 3. scale_item: Individual question/item template
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_item` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument',
    `domain_id`            BIGINT UNSIGNED     NULL
        COMMENT 'FK to scale_domain (NULL if instrument has no subscales)',
    `item_number`          INT UNSIGNED    NOT NULL
        COMMENT 'Item sequence number (1-based)',
    `item_code`            VARCHAR(50)         NULL
        COMMENT 'Machine-readable item code',
    `item_text`            TEXT            NOT NULL
        COMMENT 'Full text of the question / prompt (Chinese)',
    `item_text_en`         TEXT                NULL
        COMMENT 'English version of item text',
    `response_type`        VARCHAR(30)     NOT NULL DEFAULT 'LIKERT'
        COMMENT 'Response type: LIKERT, BINARY, NUMERIC, FREE_TEXT, PICTURE, PERFORMANCE, OBSERVATION',
    `options_json`         JSON                NULL
        COMMENT 'JSON array of response options: [{"value":0,"label":"None"},...]',
    `min_item_score`       DECIMAL(10,2)       NULL
        COMMENT 'Minimum possible score for this item',
    `max_item_score`       DECIMAL(10,2)       NULL
        COMMENT 'Maximum possible score for this item',
    `scoring_notes`        VARCHAR(500)        NULL
        COMMENT 'Item-specific scoring rules',
    `is_reverse_scored`    TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether item is reverse-scored',
    `is_required`          TINYINT(1)      NOT NULL DEFAULT 1
        COMMENT 'Whether item must be answered',
    `sort_order`           INT UNSIGNED    NOT NULL DEFAULT 0,
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_item_instrument_id` (`instrument_id`),
    KEY `idx_item_domain_id` (`domain_id`),
    KEY `idx_item_sort_order` (`sort_order`),
    UNIQUE KEY `uk_item_instrument_number` (`instrument_id`, `item_number`),
    CONSTRAINT `fk_item_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_item_domain`
        FOREIGN KEY (`domain_id`) REFERENCES `scale_domain` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Item/question template for each scale instrument';

-- ------------------------------------------------------------
-- 4. examiner: Clinician / rater qualified to administer scales
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `examiner` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `user_id`              BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to auth user table (V001)',
    `examiner_code`        VARCHAR(50)     NOT NULL
        COMMENT 'Unique examiner identifier code',
    `full_name`            VARCHAR(100)    NOT NULL
        COMMENT 'Full name in Chinese (GB/T 2261.1 convention)',
    `sex`                  VARCHAR(2)      NOT NULL DEFAULT '9'
        COMMENT 'Sex per GB/T 2261.1: 0=female, 1=male, 2=intersex, 9=unknown',
    `birth_date`           DATE                NULL
        COMMENT 'Date of birth',
    `nation`               VARCHAR(10)     NOT NULL DEFAULT '01'
        COMMENT 'Ethnicity per GB/T 3304: 01=Han, 02=Mongol, 03=Hui, ...',
    `education_code`       VARCHAR(10)     NOT NULL DEFAULT '90'
        COMMENT 'Highest education per GB/T 4658: 10=primary, 20=junior, 30=senior, 40=diploma, 50=bachelor, 60=master, 70=doctor, 90=other',
    `nationality`          VARCHAR(10)     NOT NULL DEFAULT '156'
        COMMENT 'Nationality per GB/T 2659: 156=China, 840=USA, 392=Japan, ...',
    `contact_phone`        VARCHAR(30)         NULL,
    `contact_email`        VARCHAR(200)        NULL,
    `institution`          VARCHAR(300)        NULL
        COMMENT 'Affiliated institution / hospital',
    `department`           VARCHAR(200)        NULL
        COMMENT 'Department name',
    `professional_title`   VARCHAR(100)        NULL
        COMMENT 'Professional title, e.g. Attending Physician, Professor',
    `license_number`       VARCHAR(100)        NULL
        COMMENT 'Medical / clinical license number',
    `certification_number` VARCHAR(100)        NULL
        COMMENT 'Scale administration certification number (if any)',
    `specialties`          VARCHAR(500)        NULL
        COMMENT 'Comma-separated specialties: NEUROLOGY, PSYCHIATRY, PSYCHOLOGY, NURSING, GERIATRICS',
    `years_experience`     DECIMAL(4,1)        NULL
        COMMENT 'Years of clinical experience',
    `training_completed`   TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether formal scale training is completed',
    `training_date`        DATE                NULL
        COMMENT 'Date training was completed',
    `training_provider`    VARCHAR(300)        NULL
        COMMENT 'Training provider / institution',
    `certified_instruments` VARCHAR(500)       NULL
        COMMENT 'Comma-separated instrument abbreviations examiner is certified for',
    `rater_reliability_score` DECIMAL(5,3)     NULL
        COMMENT 'Inter-rater reliability score (e.g. Cohen kappa)',
    `is_active`            TINYINT(1)      NOT NULL DEFAULT 1,
    `status`               VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'Status: ACTIVE, INACTIVE, SUSPENDED, RETIRED',
    `remarks`              VARCHAR(1000)       NULL,
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examiner_code` (`examiner_code`),
    UNIQUE KEY `uk_examiner_user_id` (`user_id`),
    KEY `idx_examiner_institution` (`institution`(100)),
    KEY `idx_examiner_is_active` (`is_active`),
    KEY `idx_examiner_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Examiner / rater qualified to administer psychological scales';

-- ------------------------------------------------------------
-- 5. scale_assessment: A single administration of a scale
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_assessment` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `assessment_uuid`      CHAR(36)        NOT NULL
        COMMENT 'Globally unique assessment identifier (UUID v4)',
    `subject_id`           BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to subject (V002)',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument',
    `examiner_id`          BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to examiner',
    `assessment_date`      DATE            NOT NULL
        COMMENT 'Date the assessment was administered',
    `assessment_time`      TIME                NULL
        COMMENT 'Time the assessment was administered',
    `assessment_mode`      VARCHAR(30)     NOT NULL DEFAULT 'IN_PERSON'
        COMMENT 'Mode: IN_PERSON, TELEPHONE, VIDEO, SELF_REPORT, PAPER, ELECTRONIC, GROUP',
    `assessment_setting`   VARCHAR(50)         NULL
        COMMENT 'Setting: OUTPATIENT, INPATIENT, EMERGENCY, COMMUNITY, HOME, RESEARCH_LAB, CLINICAL_TRIAL',
    `session_number`       INT UNSIGNED    NOT NULL DEFAULT 1
        COMMENT 'Session number (for repeated measures / longitudinal)',
    `visit_code`           VARCHAR(30)         NULL
        COMMENT 'Study visit code, e.g. BL, M06, M12, FU01',
    `total_score`          DECIMAL(10,2)       NULL
        COMMENT 'Computed total/aggregate score',
    `domain_scores_json`   JSON                NULL
        COMMENT 'JSON of domain-level scores: {"orientation":9,"attention":5,...}',
    `interpretation`       VARCHAR(200)        NULL
        COMMENT 'Clinical interpretation of the total score',
    `severity`             VARCHAR(30)         NULL
        COMMENT 'Severity level: NORMAL, MILD, MODERATE, SEVERE, VERY_SEVERE',
    `confidence_level`     VARCHAR(30)         NULL
        COMMENT 'Examiner confidence: HIGH, MEDIUM, LOW, UNCERTAIN',
    `administration_duration_min` INT UNSIGNED NULL
        COMMENT 'Actual time taken to administer, in minutes',
    `is_completed`         TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether all required items have been scored',
    `is_valid`             TINYINT(1)      NOT NULL DEFAULT 1
        COMMENT 'Whether the assessment is considered valid for analysis',
    `invalidation_reason`  VARCHAR(500)        NULL
        COMMENT 'Reason if assessment was invalidated',
    `subject_condition`    VARCHAR(500)        NULL
        COMMENT 'Notes on subject condition during assessment (alert, fatigued, agitated, etc.)',
    `medication_status`    VARCHAR(500)        NULL
        COMMENT 'Current medications that may affect performance',
    `notes`                TEXT                NULL
        COMMENT 'Free-text clinical notes',
    `source`               VARCHAR(50)     NOT NULL DEFAULT 'MANUAL'
        COMMENT 'Data source: MANUAL, IMPORT, EHR, MOBILE_APP, EDC_SYSTEM, API',
    `data_version`         VARCHAR(20)         NULL
        COMMENT 'Version stamp of the data at time of assessment',
    `signed_by`            BIGINT UNSIGNED     NULL
        COMMENT 'FK to user who signed off / validated',
    `signed_at`            DATETIME            NULL
        COMMENT 'Timestamp of sign-off',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assessment_uuid` (`assessment_uuid`),
    KEY `idx_assessment_subject_id` (`subject_id`),
    KEY `idx_assessment_instrument_id` (`instrument_id`),
    KEY `idx_assessment_examiner_id` (`examiner_id`),
    KEY `idx_assessment_date` (`assessment_date`),
    KEY `idx_assessment_subject_instrument` (`subject_id`, `instrument_id`),
    KEY `idx_assessment_subject_date` (`subject_id`, `assessment_date`),
    KEY `idx_assessment_visit_code` (`visit_code`),
    KEY `idx_assessment_is_completed` (`is_completed`),
    KEY `idx_assessment_is_valid` (`is_valid`),
    KEY `idx_assessment_mode` (`assessment_mode`),
    KEY `idx_assessment_severity` (`severity`),
    CONSTRAINT `fk_assessment_subject`
        FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_assessment_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_assessment_examiner`
        FOREIGN KEY (`examiner_id`) REFERENCES `examiner` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Scale assessment administration record: one row per assessment session';

-- ------------------------------------------------------------
-- 6. scale_data: Item-level response data (one row per item per assessment)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_data` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `assessment_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_assessment',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument (denormalized for query convenience)',
    `item_id`              BIGINT UNSIGNED     NULL
        COMMENT 'FK to scale_item template',
    `item_number`          INT UNSIGNED    NOT NULL
        COMMENT 'Item sequence number',
    `item_code`            VARCHAR(50)         NULL
        COMMENT 'Item code from template',
    `item_text_snapshot`   TEXT                NULL
        COMMENT 'Snapshot of item text at time of administration',
    `response_value`       VARCHAR(500)        NULL
        COMMENT 'Raw response: numeric score, selected option, free-text',
    `item_score`           DECIMAL(10,2)       NULL
        COMMENT 'Numerical item score after applying scoring rules',
    `response_duration_ms` INT UNSIGNED        NULL
        COMMENT 'Response time in milliseconds (if captured electronically)',
    `is_skipped`           TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether item was skipped',
    `skip_reason`          VARCHAR(200)        NULL
        COMMENT 'Reason for skipping (REFUSED, NOT_APPLICABLE, TECHNICAL_ERROR, etc.)',
    `examiner_notes`       VARCHAR(1000)       NULL
        COMMENT 'Examiner observations for this specific item',
    `audio_recording_url`  VARCHAR(500)        NULL
        COMMENT 'URL to audio recording (for verbal responses)',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_scaledata_assessment_id` (`assessment_id`),
    KEY `idx_scaledata_instrument_id` (`instrument_id`),
    KEY `idx_scaledata_item_id` (`item_id`),
    UNIQUE KEY `uk_scaledata_assessment_item` (`assessment_id`, `item_number`),
    CONSTRAINT `fk_scaledata_assessment`
        FOREIGN KEY (`assessment_id`) REFERENCES `scale_assessment` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_scaledata_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_scaledata_item`
        FOREIGN KEY (`item_id`) REFERENCES `scale_item` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Item-level response data: one row per item answered in each assessment';

-- ------------------------------------------------------------
-- 7. scale_examiner_certification: Certification audit trail
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_examiner_certification` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `examiner_id`          BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to examiner',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument',
    `certification_type`   VARCHAR(50)     NOT NULL
        COMMENT 'Type: FORMAL_TRAINING, COC_APPROVED, SELF_ATTESTED, SUPERVISED_PRACTICE',
    `certification_status` VARCHAR(30)     NOT NULL DEFAULT 'CERTIFIED'
        COMMENT 'Status: CERTIFIED, PENDING, EXPIRED, REVOKED, RECERTIFYING',
    `certification_date`   DATE                NULL,
    `expiration_date`      DATE                NULL,
    `certified_by`         VARCHAR(200)        NULL
        COMMENT 'Name of the certifying body / supervisor',
    `certificate_document_url` VARCHAR(500)    NULL
        COMMENT 'URL to scanned certificate or supporting document',
    `remarks`              VARCHAR(1000)       NULL,
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examiner_instrument_cert` (`examiner_id`, `instrument_id`),
    KEY `idx_cert_instrument_id` (`instrument_id`),
    KEY `idx_cert_status` (`certification_status`),
    KEY `idx_cert_expiration_date` (`expiration_date`),
    CONSTRAINT `fk_cert_examiner`
        FOREIGN KEY (`examiner_id`) REFERENCES `examiner` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cert_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Examiner certification tracking: which examiner is certified for which instrument';

-- ------------------------------------------------------------
-- 8. scale_longitudinal_baseline: Baseline reference for longitudinal analysis
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scale_longitudinal_baseline` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Primary key',
    `subject_id`           BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to subject',
    `instrument_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK to scale_instrument',
    `baseline_assessment_id` BIGINT UNSIGNED   NULL
        COMMENT 'FK to the assessment record used as baseline',
    `baseline_score`       DECIMAL(10,2)       NULL
        COMMENT 'Baseline total score',
    `baseline_date`        DATE                NULL
        COMMENT 'Date of baseline assessment',
    `annual_change_rate`   DECIMAL(10,4)       NULL
        COMMENT 'Annualized rate of change in score',
    `is_baseline_locked`   TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Whether baseline is locked/frozen',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_longitudinal_subject_instrument` (`subject_id`, `instrument_id`),
    KEY `idx_baseline_assessment_id` (`baseline_assessment_id`),
    KEY `idx_baseline_date` (`baseline_date`),
    CONSTRAINT `fk_baseline_subject`
        FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_baseline_instrument`
        FOREIGN KEY (`instrument_id`) REFERENCES `scale_instrument` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_baseline_assessment`
        FOREIGN KEY (`baseline_assessment_id`) REFERENCES `scale_assessment` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Longitudinal tracking: baseline scores and annual change rates per subject per instrument';
