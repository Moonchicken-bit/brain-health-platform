CREATE TABLE IF NOT EXISTS assessment_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    visit_code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assigned_by VARCHAR(100) NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    submitted_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_assessment_task_session (session_id),
    KEY idx_assessment_task_subject_status (subject_id, status),
    CONSTRAINT fk_assessment_task_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_assessment_task_session FOREIGN KEY (session_id) REFERENCES `session`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO assessment_task (subject_id, session_id, visit_code, status, assigned_by, assigned_at)
SELECT s.subject_id, s.id, COALESCE(NULLIF(s.visit_label, ''), CONCAT('V', s.visit_number)),
       CASE WHEN EXISTS (
           SELECT 1 FROM scale_assessment sa
           WHERE sa.session_id=s.id AND UPPER(sa.data_entry_status)='COMPLETE'
       ) THEN 'SUBMITTED' ELSE 'PENDING' END,
       s.registered_by, COALESCE(s.created_at, NOW())
FROM `session` s
WHERE NOT EXISTS (SELECT 1 FROM assessment_task t WHERE t.session_id=s.id);
