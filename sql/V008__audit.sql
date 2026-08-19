-- ============================================================================
-- Flyway Migration V008: Audit Module
-- Description: Audit log, data access log, and consent record tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. audit_log — 操作审计日志表 (Operation Audit Log)
-- Records every auditable action performed by users across the platform.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT UNSIGNED          DEFAULT NULL    COMMENT '操作人用户ID，关联sys_user',
    username        VARCHAR(64)    NOT NULL                  COMMENT '操作人用户名（冗余，便于查询）',
    real_name       VARCHAR(64)              DEFAULT NULL    COMMENT '操作人真实姓名（冗余）',
    operation_type  VARCHAR(32)    NOT NULL                  COMMENT '操作类型：LOGIN, LOGOUT, CREATE, UPDATE, DELETE, VIEW, EXPORT, IMPORT, APPROVE, REJECT, ASSIGN, REVOKE',
    target_type     VARCHAR(48)    NOT NULL                  COMMENT '目标资源类型：SUBJECT, SCALE_RECORD, IMAGING_EXAM, LAB_PANEL, GENETIC_PROFILE, DIAGNOSIS, USER, ROLE, CONSENT, REPORT',
    target_id       BIGINT UNSIGNED          DEFAULT NULL    COMMENT '目标资源ID',
    target_label    VARCHAR(256)             DEFAULT NULL    COMMENT '目标资源描述（冗余，便于阅读）',
    operation_detail JSON                    DEFAULT NULL    COMMENT '操作详情（JSON格式，记录变更前后数据快照）',
    operation_ip    VARCHAR(45)              DEFAULT NULL    COMMENT '操作来源IP（IPv4/IPv6）',
    operation_time  DATETIME       NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    operation_result VARCHAR(16)   NOT NULL  DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS, FAILURE, PARTIAL',
    error_message   TEXT                      DEFAULT NULL    COMMENT '失败时的错误信息',
    user_agent      VARCHAR(512)             DEFAULT NULL    COMMENT '浏览器/客户端User-Agent',
    trace_id        VARCHAR(64)              DEFAULT NULL    COMMENT '分布式链路追踪ID',
    session_id      VARCHAR(128)             DEFAULT NULL    COMMENT '会话ID',
    duration_ms     INT UNSIGNED             DEFAULT NULL    COMMENT '操作耗时（毫秒）',
    created_at      DATETIME       NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at      DATETIME       NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

    PRIMARY KEY (id),
    INDEX idx_audit_log_user_id (user_id),
    INDEX idx_audit_log_operation_type (operation_type),
    INDEX idx_audit_log_target_type (target_type),
    INDEX idx_audit_log_target_id (target_id),
    INDEX idx_audit_log_operation_time (operation_time),
    INDEX idx_audit_log_operation_result (operation_result),
    INDEX idx_audit_log_trace_id (trace_id),
    INDEX idx_audit_log_created_at (created_at),
    INDEX idx_audit_log_target_lookup (target_type, target_id),
    INDEX idx_audit_log_user_time (user_id, operation_time),

    CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作审计日志表 — 记录平台所有敏感操作';

-- ----------------------------------------------------------------------------
-- 2. data_access_log — 数据访问日志表 (Data Access Log)
-- Tracks every instance of data viewing, downloading, or exporting,
-- especially PHI/PII access to subject-level data.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_access_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT UNSIGNED NOT NULL                 COMMENT '访问用户ID，关联sys_user',
    username        VARCHAR(64)     NOT NULL                 COMMENT '访问用户名（冗余）',
    subject_id      BIGINT UNSIGNED NOT NULL                 COMMENT '被访问的受试者ID，关联subject',
    subject_code    VARCHAR(64)               DEFAULT NULL   COMMENT '受试者编码（冗余）',
    access_type     VARCHAR(32)     NOT NULL                 COMMENT '访问类型：VIEW, DOWNLOAD, EXPORT, API_QUERY, BATCH_EXPORT',
    data_category   VARCHAR(48)     NOT NULL                 COMMENT '数据类别：DEMOGRAPHIC, SCALE_ASSESSMENT, IMAGING, LAB_RESULT, GENETIC, DIAGNOSIS, CONSENT, ALL',
    data_detail     JSON                      DEFAULT NULL   COMMENT '访问的数据详情（JSON：字段列表、记录数、时间范围等）',
    access_reason   VARCHAR(64)     NOT NULL  DEFAULT 'RESEARCH' COMMENT '访问目的：RESEARCH, CLINICAL_CARE, QUALITY_CONTROL, AUDIT_REVIEW, DATA_EXPORT, EMERGENCY',
    access_ip       VARCHAR(45)               DEFAULT NULL   COMMENT '访问来源IP',
    access_result   VARCHAR(16)     NOT NULL  DEFAULT 'GRANTED' COMMENT '访问结果：GRANTED, DENIED, MASKED',
    denial_reason   TEXT                      DEFAULT NULL   COMMENT '拒绝/脱敏原因',
    access_token_id VARCHAR(128)              DEFAULT NULL   COMMENT 'OAuth2 token标识',
    session_id      VARCHAR(128)              DEFAULT NULL   COMMENT '会话ID',
    trace_id        VARCHAR(64)               DEFAULT NULL   COMMENT '分布式链路追踪ID',
    created_at      DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at      DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

    PRIMARY KEY (id),
    INDEX idx_dal_user_id (user_id),
    INDEX idx_dal_subject_id (subject_id),
    INDEX idx_dal_access_type (access_type),
    INDEX idx_dal_data_category (data_category),
    INDEX idx_dal_access_result (access_result),
    INDEX idx_dal_access_reason (access_reason),
    INDEX idx_dal_created_at (created_at),
    INDEX idx_dal_subject_access (subject_id, created_at),
    INDEX idx_dal_user_access (user_id, created_at),
    INDEX idx_dal_subject_category (subject_id, data_category),

    CONSTRAINT fk_dal_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_dal_subject
        FOREIGN KEY (subject_id) REFERENCES subject (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据访问日志表 — 记录所有受试者数据的访问行为';

-- ----------------------------------------------------------------------------
-- 3. consent_record — 知情同意记录表 (Informed Consent Record)
-- Manages subject consent for data collection, research use, data sharing,
-- genetic testing, and imaging studies. Supports versioned consent forms.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consent_record (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    subject_id              BIGINT UNSIGNED NOT NULL                 COMMENT '受试者ID，关联subject',
    subject_code            VARCHAR(64)               DEFAULT NULL   COMMENT '受试者编码（冗余）',
    consent_type            VARCHAR(48)     NOT NULL                 COMMENT '同意类型：DATA_COLLECTION, RESEARCH_USE, DATA_SHARING, GENETIC_TESTING, IMAGING, BIOMARKER_ANALYSIS, BIOBANK_STORAGE, FOLLOW_UP, SECONDARY_USE, WITHDRAWAL',
    consent_version         VARCHAR(16)     NOT NULL  DEFAULT '1.0'  COMMENT '知情同意书版本号',
    consent_form_name       VARCHAR(256)              DEFAULT NULL   COMMENT '知情同意书名称/标题',
    consent_status          VARCHAR(24)     NOT NULL  DEFAULT 'ACTIVE' COMMENT '同意状态：PENDING, ACTIVE, EXPIRED, REVOKED, SUSPENDED',
    consent_start_date      DATE            NOT NULL                 COMMENT '同意生效日期',
    consent_end_date        DATE                      DEFAULT NULL   COMMENT '同意失效日期（有期限的同意）',
    consent_document_path   VARCHAR(512)              DEFAULT NULL   COMMENT '已签署知情同意书文件路径（MinIO/OSS）',
    consent_document_hash   VARCHAR(128)              DEFAULT NULL   COMMENT '文件哈希值（SHA-256）',
    signed_by_subject       TINYINT(1)      NOT NULL  DEFAULT 0       COMMENT '受试者本人签署：0-否, 1-是',
    subject_sign_date       DATE                      DEFAULT NULL   COMMENT '受试者签署日期',
    signed_by_guardian      TINYINT(1)      NOT NULL  DEFAULT 0       COMMENT '法定监护人签署：0-否, 1-是',
    guardian_name           VARCHAR(64)               DEFAULT NULL   COMMENT '法定监护人姓名',
    guardian_relationship   VARCHAR(32)               DEFAULT NULL   COMMENT '与受试者关系：PARENT, SPOUSE, ADULT_CHILD, SIBLING, LEGAL_GUARDIAN, OTHER',
    guardian_sign_date      DATE                      DEFAULT NULL   COMMENT '监护人签署日期',
    signed_by_witness       TINYINT(1)      NOT NULL  DEFAULT 0       COMMENT '见证人签署：0-否, 1-是',
    witness_name            VARCHAR(64)               DEFAULT NULL   COMMENT '见证人姓名',
    witness_role            VARCHAR(32)               DEFAULT NULL   COMMENT '见证人角色/职务',
    witness_sign_date       DATE                      DEFAULT NULL   COMMENT '见证人签署日期',
    consent_obtained_by     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '获取同意的研究人员ID，关联sys_user',
    consent_obtained_date   DATE                      DEFAULT NULL   COMMENT '获取同意日期',
    consent_notes           TEXT                      DEFAULT NULL   COMMENT '同意备注说明',
    consent_scope           JSON                      DEFAULT NULL   COMMENT '同意范围明细（JSON：数据类别、使用限制等）',
    ethical_approval_ref    VARCHAR(128)              DEFAULT NULL   COMMENT '伦理审批编号',
    revocation_date         DATETIME                  DEFAULT NULL   COMMENT '撤回日期',
    revocation_reason       TEXT                      DEFAULT NULL   COMMENT '撤回原因',
    revocation_document_path VARCHAR(512)             DEFAULT NULL   COMMENT '撤回同意书文件路径',
    is_current              TINYINT(1)      NOT NULL  DEFAULT 1       COMMENT '是否为当前有效版本：0-否, 1-是',
    created_by              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID，关联sys_user',
    updated_by              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '最后更新人ID，关联sys_user',
    created_at              DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at              DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

    PRIMARY KEY (id),
    INDEX idx_cr_subject_id (subject_id),
    INDEX idx_cr_consent_type (consent_type),
    INDEX idx_cr_consent_status (consent_status),
    INDEX idx_cr_consent_start_date (consent_start_date),
    INDEX idx_cr_consent_end_date (consent_end_date),
    INDEX idx_cr_is_current (is_current),
    INDEX idx_cr_consent_version (consent_version),
    INDEX idx_cr_revocation_date (revocation_date),
    INDEX idx_cr_subject_current (subject_id, is_current),
    INDEX idx_cr_subject_type (subject_id, consent_type),
    INDEX idx_cr_obtained_by (consent_obtained_by),
    INDEX idx_cr_ethical_approval (ethical_approval_ref),

    CONSTRAINT fk_cr_subject
        FOREIGN KEY (subject_id) REFERENCES subject (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_cr_obtained_by
        FOREIGN KEY (consent_obtained_by) REFERENCES `user` (id)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT fk_cr_created_by
        FOREIGN KEY (created_by) REFERENCES `user` (id)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT fk_cr_updated_by
        FOREIGN KEY (updated_by) REFERENCES `user` (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知情同意记录表 — 管理受试者各类知情同意及其版本';
