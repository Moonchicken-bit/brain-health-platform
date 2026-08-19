-- ============================================================
-- V007: RBAC — Role-Based Access Control
-- Brain Health Platform | Flyway Migration
-- Tables: user, role, permission, role_permission, user_role
-- ============================================================

-- ------------------------------------------------------------
-- 1. user
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    `user_uuid`           CHAR(36)        NOT NULL                 COMMENT 'External/API-facing UUID',
    `username`            VARCHAR(64)     NOT NULL                 COMMENT 'Login username (unique)',
    `password_hash`       VARCHAR(256)    NOT NULL                 COMMENT 'BCrypt/Argon2 password hash',
    `email`               VARCHAR(128)    DEFAULT NULL             COMMENT 'Email address',
    `phone`               VARCHAR(32)     DEFAULT NULL             COMMENT 'Phone number',
    `real_name`           VARCHAR(64)     DEFAULT NULL             COMMENT 'Real name (Chinese characters supported)',
    `nickname`            VARCHAR(64)     DEFAULT NULL             COMMENT 'Display nickname',
    `avatar_url`          VARCHAR(512)    DEFAULT NULL             COMMENT 'Avatar image URL',
    `sex`                 TINYINT UNSIGNED DEFAULT 0               COMMENT 'Sex: 0=unknown, 1=male, 2=female (GB/T 2261.1)',
    `department`          VARCHAR(128)    DEFAULT NULL             COMMENT 'Department / affiliation',
    `title`               VARCHAR(64)     DEFAULT NULL             COMMENT 'Professional title (e.g., Attending Physician, Professor)',
    `status`              TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT 'Account status: 0=disabled, 1=active, 2=locked',
    `last_login_at`       DATETIME        DEFAULT NULL             COMMENT 'Last successful login timestamp',
    `last_login_ip`       VARCHAR(64)     DEFAULT NULL             COMMENT 'Last login IP address',
    `password_changed_at` DATETIME        DEFAULT NULL             COMMENT 'When password was last changed',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_uuid`    (`user_uuid`),
    UNIQUE KEY `uk_username`     (`username`),
    UNIQUE KEY `uk_email`        (`email`),
    INDEX      `idx_phone`       (`phone`),
    INDEX      `idx_status`      (`status`),
    INDEX      `idx_real_name`   (`real_name`),
    INDEX      `idx_department`  (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System user accounts';

-- ------------------------------------------------------------
-- 2. role
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    `role_code`   VARCHAR(64)     NOT NULL                 COMMENT 'Role code (unique, e.g., ROLE_ADMIN, ROLE_PI)',
    `role_name`   VARCHAR(128)    NOT NULL                 COMMENT 'Human-readable role name',
    `role_type`   TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT 'Role type: 1=system (non-deletable), 2=custom',
    `description` VARCHAR(512)    DEFAULT NULL             COMMENT 'Role description',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT 'Status: 0=disabled, 1=enabled',
    `sort_order`  INT             NOT NULL DEFAULT 0       COMMENT 'Display sort order',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    INDEX      `idx_role_type` (`role_type`),
    INDEX      `idx_status`    (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Authorization roles';

-- ------------------------------------------------------------
-- 3. permission
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    `parent_id`       BIGINT UNSIGNED DEFAULT NULL             COMMENT 'Parent permission (for hierarchical menu/button trees)',
    `permission_code` VARCHAR(128)    NOT NULL                 COMMENT 'Permission code (unique, e.g., subject:read, scale:admin)',
    `permission_name` VARCHAR(256)    NOT NULL                 COMMENT 'Human-readable permission name',
    `resource_type`   TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT 'Resource type: 1=menu/catalog, 2=page/route, 3=button, 4=api',
    `path`            VARCHAR(256)    DEFAULT NULL             COMMENT 'Frontend route path or API path pattern',
    `method`          VARCHAR(16)     DEFAULT NULL             COMMENT 'HTTP method: GET, POST, PUT, DELETE, etc. (applies to resource_type=4)',
    `component`       VARCHAR(256)    DEFAULT NULL             COMMENT 'Frontend component path or Vue component name',
    `icon`            VARCHAR(64)     DEFAULT NULL             COMMENT 'Menu/button icon identifier',
    `description`     VARCHAR(512)    DEFAULT NULL             COMMENT 'Permission description',
    `sort_order`      INT             NOT NULL DEFAULT 0       COMMENT 'Display sort order',
    `status`          TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT 'Status: 0=disabled, 1=enabled',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`),
    INDEX      `idx_parent_id`       (`parent_id`),
    INDEX      `idx_resource_type`   (`resource_type`),
    INDEX      `idx_status`          (`status`),
    INDEX      `idx_sort_order`      (`sort_order`),
    CONSTRAINT `fk_permission_parent` FOREIGN KEY (`parent_id`) REFERENCES `permission` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permissions / access-control entries';

-- ------------------------------------------------------------
-- 4. role_permission  (many-to-many: role <-> permission)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    `role_id`       BIGINT UNSIGNED NOT NULL                 COMMENT 'FK: role.id',
    `permission_id` BIGINT UNSIGNED NOT NULL                 COMMENT 'FK: permission.id',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX      `idx_permission_id`  (`permission_id`),
    CONSTRAINT `fk_rp_role`       FOREIGN KEY (`role_id`)       REFERENCES `role`       (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role-to-permission assignment';

-- ------------------------------------------------------------
-- 5. user_role  (many-to-many: user <-> role)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    `user_id`    BIGINT UNSIGNED NOT NULL                 COMMENT 'FK: user.id',
    `role_id`    BIGINT UNSIGNED NOT NULL                 COMMENT 'FK: role.id',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX      `idx_role_id`  (`role_id`),
    CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-to-role assignment';
