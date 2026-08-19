CREATE TABLE IF NOT EXISTS lab_report_upload (
    id VARCHAR(36) PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lab_report_subject_session (subject_id, session_id),
    INDEX idx_lab_report_status (status)
);
