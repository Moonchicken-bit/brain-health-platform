CREATE TABLE IF NOT EXISTS form_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    module VARCHAR(40) NOT NULL,
    scope_type VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    scope_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_code_version (code, version),
    KEY idx_form_module_status (module, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS field_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    form_id BIGINT UNSIGNED NOT NULL,
    field_code VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    field_type VARCHAR(30) NOT NULL,
    unit VARCHAR(40) NULL,
    default_value VARCHAR(500) NULL,
    options_json JSON NULL,
    validation_json JSON NULL,
    required_flag TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_field_code (form_id, field_code),
    CONSTRAINT fk_field_form FOREIGN KEY (form_id) REFERENCES form_definition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS field_value (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    field_id BIGINT UNSIGNED NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    value_json JSON NULL,
    form_version INT NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_entity (field_id, entity_type, entity_id),
    CONSTRAINT fk_value_field FOREIGN KEY (field_id) REFERENCES field_definition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO form_definition (code, name, module, status, version)
SELECT 'GENETICS_SAMPLE', '遗传样本扩展字段', 'GENETICS', 'PUBLISHED', 1
WHERE NOT EXISTS (
    SELECT 1 FROM form_definition WHERE code='GENETICS_SAMPLE' AND version=1
);
