CREATE TABLE IF NOT EXISTS visit_attachment (
    id            VARCHAR(36)     NOT NULL,
    subject_id    BIGINT          NOT NULL,
    visit_code    VARCHAR(32)     NOT NULL,
    field_code    VARCHAR(160)    NOT NULL,
    original_name VARCHAR(255)    NOT NULL,
    object_key    VARCHAR(500)    NOT NULL,
    content_type  VARCHAR(160)    DEFAULT NULL,
    size          BIGINT          NOT NULL,
    created_at    DATETIME        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_attachment_object_key (object_key),
    KEY idx_visit_attachment_subject (subject_id),
    KEY idx_visit_attachment_context (subject_id, visit_code, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='访视字段附件元数据';
