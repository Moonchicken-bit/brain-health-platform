CREATE TABLE IF NOT EXISTS visit_form_item_override (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_code VARCHAR(20) NOT NULL,
    scale_code VARCHAR(80) NOT NULL,
    item_code VARCHAR(160) NOT NULL,
    label_override VARCHAR(500) NULL,
    required_override TINYINT(1) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    version INT NOT NULL DEFAULT 1,
    updated_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_item_override (visit_code,item_code,version),
    KEY idx_visit_item_override_lookup (visit_code,scale_code,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
