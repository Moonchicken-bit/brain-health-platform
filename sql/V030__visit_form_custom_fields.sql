CREATE TABLE IF NOT EXISTS visit_form_custom_field (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_code VARCHAR(20) NOT NULL,
    section_code VARCHAR(40) NOT NULL DEFAULT 'CUSTOM',
    field_code VARCHAR(100) NOT NULL,
    label VARCHAR(300) NOT NULL,
    field_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    unit VARCHAR(40) NULL,
    options_json JSON NULL,
    required_flag TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_custom_code_version (visit_code,field_code,version),
    KEY idx_visit_custom_status (visit_code,status,sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
