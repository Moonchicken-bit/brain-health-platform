CREATE TABLE IF NOT EXISTS unified_import_batch (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ANALYZING',
    total_files INT NOT NULL DEFAULT 0,
    included_files INT NOT NULL DEFAULT 0,
    uploaded_by BIGINT UNSIGNED NULL,
    confirmed_by BIGINT UNSIGNED NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_package_hash (subject_id, session_id, file_sha256),
    KEY idx_import_visit (session_id, created_at),
    CONSTRAINT fk_import_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_import_session FOREIGN KEY (session_id) REFERENCES session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS unified_import_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id BIGINT UNSIGNED NOT NULL,
    relative_path VARCHAR(1000) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_sha256 CHAR(64) NULL,
    detected_module VARCHAR(30) NOT NULL,
    confirmed_module VARCHAR(30) NULL,
    confidence DECIMAL(5,2) NOT NULL DEFAULT 0,
    included TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'IDENTIFIED',
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_import_item_batch (batch_id,detected_module),
    CONSTRAINT fk_import_item_batch FOREIGN KEY (batch_id)
        REFERENCES unified_import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS unified_import_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id BIGINT UNSIGNED NOT NULL,
    module VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    item_count INT NOT NULL DEFAULT 0,
    result_json JSON NULL,
    error_message TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_job_module (batch_id,module),
    CONSTRAINT fk_import_job_batch FOREIGN KEY (batch_id)
        REFERENCES unified_import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
