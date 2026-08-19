-- Normalize legacy audit_log columns to the schema introduced by V008.
-- Every ALTER is conditional so this migration is safe for both upgraded and fresh databases.

SET @db := DATABASE();

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='action')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='operation_type'),
  'ALTER TABLE audit_log CHANGE COLUMN action operation_type VARCHAR(32) NOT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='resource_type')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='target_type'),
  'ALTER TABLE audit_log CHANGE COLUMN resource_type target_type VARCHAR(48) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='resource_id')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='target_id'),
  'ALTER TABLE audit_log CHANGE COLUMN resource_id target_id BIGINT UNSIGNED NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='detail')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='operation_detail'),
  'ALTER TABLE audit_log CHANGE COLUMN detail operation_detail LONGTEXT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='ip_address')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='operation_ip'),
  'ALTER TABLE audit_log CHANGE COLUMN ip_address operation_ip VARCHAR(45) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='status')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='operation_result'),
  'ALTER TABLE audit_log CHANGE COLUMN status operation_result VARCHAR(16) NOT NULL DEFAULT ''SUCCESS''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='audit_log' AND column_name='user_id'),
  'ALTER TABLE audit_log ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER id',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE audit_log a
JOIN `user` u ON u.username = a.username
SET a.user_id = u.id
WHERE a.user_id IS NULL;

