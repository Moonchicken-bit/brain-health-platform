CREATE TABLE IF NOT EXISTS visit_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    project_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_version_id BIGINT UNSIGNED NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_template_code (code),
    KEY idx_visit_template_project (project_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS visit_template_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    version_no INT NOT NULL,
    visit_code VARCHAR(50) NOT NULL,
    visit_name VARCHAR(200) NOT NULL,
    allow_unified_upload TINYINT(1) NOT NULL DEFAULT 1,
    required_modules JSON NULL,
    patient_deadline_days INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_template_version (template_id,version_no),
    CONSTRAINT fk_visit_template_version_template FOREIGN KEY (template_id)
        REFERENCES visit_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS visit_template_scale (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_version_id BIGINT UNSIGNED NOT NULL,
    scale_code VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    required_flag TINYINT(1) NOT NULL DEFAULT 1,
    patient_visible TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_template_scale (template_version_id,scale_code),
    CONSTRAINT fk_visit_template_scale_version FOREIGN KEY (template_version_id)
        REFERENCES visit_template_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE session
    ADD COLUMN visit_template_version_id BIGINT UNSIGNED NULL AFTER visit_number,
    ADD COLUMN form_snapshot_json JSON NULL AFTER visit_template_version_id;

ALTER TABLE assessment_task
    ADD COLUMN template_version_id BIGINT UNSIGNED NULL AFTER visit_code,
    ADD COLUMN scale_codes JSON NULL AFTER template_version_id,
    ADD COLUMN due_at DATETIME NULL AFTER assigned_at;
