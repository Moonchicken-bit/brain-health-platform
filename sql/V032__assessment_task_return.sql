ALTER TABLE assessment_task
    ADD COLUMN return_reason VARCHAR(1000) NULL AFTER submitted_at,
    ADD COLUMN returned_at DATETIME NULL AFTER return_reason;
