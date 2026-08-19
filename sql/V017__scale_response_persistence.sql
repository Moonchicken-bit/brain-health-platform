-- Compatibility columns used by the scale-service visit workflow.
ALTER TABLE scale_assessment
    ADD COLUMN session_id BIGINT NULL,
    ADD COLUMN data_entry_status VARCHAR(30) NOT NULL DEFAULT 'Incomplete',
    ADD COLUMN administration_mode VARCHAR(30) NULL,
    MODIFY COLUMN assessment_uuid CHAR(36) NULL,
    MODIFY COLUMN examiner_id BIGINT UNSIGNED NULL;

ALTER TABLE scale_data
    MODIFY COLUMN item_code VARCHAR(200) NULL,
    MODIFY COLUMN response_value TEXT NULL;
