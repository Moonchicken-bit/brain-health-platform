CREATE TABLE IF NOT EXISTS imaging_processing_task (
    task_id VARCHAR(80) NOT NULL,
    imaging_session_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    logs LONGTEXT NULL,
    output_prefix VARCHAR(512) NULL,
    error TEXT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    INDEX idx_processing_status (status),
    INDEX idx_processing_session (imaging_session_id),
    INDEX idx_processing_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
