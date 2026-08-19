-- Compatibility columns consumed by lab-service. V001 keeps the richer
-- canonical name/name_zh and reference_range_* columns.
ALTER TABLE lab_test_item
    ADD COLUMN item_name VARCHAR(200) NULL,
    ADD COLUMN reference_low DECIMAL(15,4) NULL,
    ADD COLUMN reference_high DECIMAL(15,4) NULL;

UPDATE lab_test_item
SET item_name = COALESCE(NULLIF(name_zh, ''), name),
    reference_low = reference_range_low,
    reference_high = reference_range_high;

ALTER TABLE lab_test_item
    MODIFY COLUMN item_name VARCHAR(200) NOT NULL,
    ADD UNIQUE KEY uk_item_name (item_name);

-- Align the canonical V001 result table with the compact API entity while
-- retaining the original structured columns for backward compatibility.
ALTER TABLE lab_result
    ADD COLUMN lab_test_id BIGINT NULL,
    ADD COLUMN result VARCHAR(500) NULL,
    ADD COLUMN reference_range VARCHAR(200) NULL,
    ADD COLUMN collection_date DATE NULL,
    ADD COLUMN technician_id BIGINT NULL,
    MODIFY COLUMN lab_test_item_id BIGINT UNSIGNED NULL;

UPDATE lab_result
SET lab_test_id = lab_test_item_id,
    result = COALESCE(result_text, CAST(result_value AS CHAR)),
    collection_date = DATE(test_date)
WHERE lab_test_item_id IS NOT NULL;
