SET @otp_secret_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN otp_secret VARCHAR(512) NULL COMMENT ''AES-GCM encrypted TOTP secret''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'otp_secret'
);
PREPARE otp_secret_stmt FROM @otp_secret_sql;
EXECUTE otp_secret_stmt;
DEALLOCATE PREPARE otp_secret_stmt;

SET @otp_recovery_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN otp_recovery_codes TEXT NULL COMMENT ''BCrypt recovery-code hashes''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'otp_recovery_codes'
);
PREPARE otp_recovery_stmt FROM @otp_recovery_sql;
EXECUTE otp_recovery_stmt;
DEALLOCATE PREPARE otp_recovery_stmt;
