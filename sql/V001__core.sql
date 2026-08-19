-- ============================================================================
-- V001__core.sql
-- Flyway Migration - Core Schema for Brain Health Platform
-- Description: 脑健康平台核心表结构
-- Author: Brain Health Platform Team
-- Date: 2026-06-02
-- ============================================================================

-- ============================================================================
-- Section 0: Database Defaults
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- Section 1: Code / Reference Tables (字典/编码表)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 1.1 sex_code - 性别代码 (GB/T 2261.1)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sex_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '性别代码',
    name VARCHAR(50) NOT NULL COMMENT '性别名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sex_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='性别代码表 (GB/T 2261.1)';

-- --------------------------------------------------------------------------
-- 1.2 nation_code - 民族代码 (GB/T 3304)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nation_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '民族代码',
    name VARCHAR(50) NOT NULL COMMENT '民族名称',
    pinyin VARCHAR(100) COMMENT '拼音',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nation_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='民族代码表 (GB/T 3304)';

-- --------------------------------------------------------------------------
-- 1.3 education_code - 学历代码 (GB/T 4658)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS education_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '学历代码',
    name VARCHAR(50) NOT NULL COMMENT '学历名称',
    level INT COMMENT '学历层次 (数值越大越高)',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_education_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学历代码表 (GB/T 4658)';

-- --------------------------------------------------------------------------
-- 1.4 nationality_code - 国籍代码 (GB/T 2659 / ISO 3166-1)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nationality_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '国籍代码 (ISO 3166-1 alpha-3)',
    name VARCHAR(100) NOT NULL COMMENT '国籍名称',
    name_zh VARCHAR(100) COMMENT '国籍中文名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nationality_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国籍代码表 (GB/T 2659 / ISO 3166-1)';

-- --------------------------------------------------------------------------
-- 1.5 marital_status_code - 婚姻状况代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marital_status_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '婚姻状况代码',
    name VARCHAR(50) NOT NULL COMMENT '婚姻状况名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marital_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='婚姻状况代码表';

-- --------------------------------------------------------------------------
-- 1.6 blood_type_code - 血型代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blood_type_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '血型代码',
    name VARCHAR(50) NOT NULL COMMENT '血型名称 (含Rh因子)',
    abo_type VARCHAR(5) COMMENT 'ABO分型',
    rh_type VARCHAR(5) COMMENT 'Rh分型',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_blood_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='血型代码表';

-- --------------------------------------------------------------------------
-- 1.7 insurance_type_code - 医保类型代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS insurance_type_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '医保类型代码',
    name VARCHAR(100) NOT NULL COMMENT '医保类型名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_insurance_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医保类型代码表';

-- --------------------------------------------------------------------------
-- 1.8 examination_type_code - 检查/访视类型代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS examination_type_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '检查类型代码',
    name VARCHAR(100) NOT NULL COMMENT '检查类型名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检查/访视类型代码表';

-- --------------------------------------------------------------------------
-- 1.9 sample_type_code - 生物样本类型代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sample_type_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL COMMENT '样本类型代码',
    name VARCHAR(100) NOT NULL COMMENT '样本类型名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sample_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生物样本类型代码表';

-- --------------------------------------------------------------------------
-- 1.10 diagnosis_type_code - 诊断类型代码
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diagnosis_type_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL COMMENT '诊断类型代码',
    name VARCHAR(100) NOT NULL COMMENT '诊断类型名称',
    description VARCHAR(200) COMMENT '说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnosis_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断类型代码表';

-- ============================================================================
-- Section 2: Core Entity Tables (核心业务表)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 2.1 institution - 机构/医院
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS institution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '机构全称',
    short_name VARCHAR(100) COMMENT '机构简称',
    code VARCHAR(50) COMMENT '机构代码/编号',
    institution_type VARCHAR(50) COMMENT '机构类型 (医院/研究院/社区中心)',
    address TEXT COMMENT '地址',
    phone VARCHAR(30) COMMENT '联系电话',
    email VARCHAR(100) COMMENT '联系邮箱',
    website VARCHAR(200) COMMENT '网站',
    description TEXT COMMENT '描述',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/inactive/suspended',
    parent_id BIGINT UNSIGNED COMMENT '上级机构ID (自引用)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_institution_code (code),
    INDEX idx_institution_name (name),
    INDEX idx_institution_status (status),
    INDEX idx_institution_parent (parent_id),
    CONSTRAINT fk_institution_parent FOREIGN KEY (parent_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构/医院表 - Institution';

-- --------------------------------------------------------------------------
-- 2.2 project - 研究项目
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(300) NOT NULL COMMENT '项目名称',
    short_name VARCHAR(100) COMMENT '项目简称/缩写',
    code VARCHAR(50) COMMENT '项目编号',
    description TEXT COMMENT '项目描述',
    project_type VARCHAR(50) COMMENT '项目类型: observational/interventional/registry/cohort_study',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态: draft/active/completed/suspended/cancelled',
    start_date DATE COMMENT '开始日期',
    end_date DATE COMMENT '结束日期',
    lead_institution_id BIGINT UNSIGNED COMMENT '牵头机构ID',
    principal_investigator VARCHAR(100) COMMENT '主要研究者姓名',
    ethics_approval_number VARCHAR(100) COMMENT '伦理审批号',
    approval_date DATE COMMENT '批准日期',
    target_enrollment INT COMMENT '目标入组人数',
    actual_enrollment INT DEFAULT 0 COMMENT '实际入组人数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (code),
    INDEX idx_project_name (name),
    INDEX idx_project_status (status),
    INDEX idx_project_start_date (start_date),
    INDEX idx_project_lead_institution (lead_institution_id),
    CONSTRAINT fk_project_institution FOREIGN KEY (lead_institution_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='研究项目表 - Project';

-- --------------------------------------------------------------------------
-- 2.3 cohort - 队列/分组
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cohort (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '队列名称',
    code VARCHAR(50) COMMENT '队列编号',
    description TEXT COMMENT '队列描述',
    cohort_type VARCHAR(50) COMMENT '队列类型: case/control/exposure/treatment_arm/observational',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/completed/archived',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目ID',
    enrollment_criteria TEXT COMMENT '入组标准描述',
    target_size INT COMMENT '目标样本量',
    current_size INT DEFAULT 0 COMMENT '当前样本量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cohort_project_code (project_id, code),
    INDEX idx_cohort_name (name),
    INDEX idx_cohort_status (status),
    INDEX idx_cohort_type (cohort_type),
    CONSTRAINT fk_cohort_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队列/分组表 - Cohort';

-- --------------------------------------------------------------------------
-- 2.4 subject - 受试者/研究对象
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subject (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_code VARCHAR(50) NOT NULL COMMENT '受试者编号 (脱敏)',
    first_name VARCHAR(100) COMMENT '名 (可脱敏)',
    last_name VARCHAR(100) COMMENT '姓 (可脱敏)',
    name_pinyin VARCHAR(200) COMMENT '姓名拼音',
    sex_code_id BIGINT UNSIGNED COMMENT '性别代码ID',
    birth_date DATE COMMENT '出生日期',
    age_at_enrollment INT COMMENT '入组时年龄',
    nation_code_id BIGINT UNSIGNED COMMENT '民族代码ID',
    education_code_id BIGINT UNSIGNED COMMENT '学历代码ID',
    nationality_code_id BIGINT UNSIGNED COMMENT '国籍代码ID',
    marital_status_code_id BIGINT UNSIGNED COMMENT '婚姻状况代码ID',
    blood_type_code_id BIGINT UNSIGNED COMMENT '血型代码ID',
    insurance_type_code_id BIGINT UNSIGNED COMMENT '医保类型代码ID',
    id_card_hash VARCHAR(128) COMMENT '身份证号哈希 (SHA-256, 脱敏)',
    phone_hash VARCHAR(128) COMMENT '电话号码哈希 (脱敏)',
    address_city VARCHAR(100) COMMENT '居住城市',
    address_district VARCHAR(100) COMMENT '居住区县',
    handedness VARCHAR(10) COMMENT '利手: left/right/ambidextrous',
    education_years DECIMAL(4,1) COMMENT '受教育年限',
    height_cm DECIMAL(5,1) COMMENT '身高(cm)',
    weight_kg DECIMAL(5,1) COMMENT '体重(kg)',
    bmi DECIMAL(4,1) COMMENT 'BMI',
    enrollment_date DATE COMMENT '入组日期',
    enrollment_institution_id BIGINT UNSIGNED COMMENT '入组机构ID',
    status VARCHAR(20) NOT NULL DEFAULT 'screening' COMMENT '状态: screening/enrolled/active/completed/withdrawn/deceased/lost_to_followup',
    withdrawal_date DATE COMMENT '退出日期',
    withdrawal_reason VARCHAR(200) COMMENT '退出原因',
    is_consented TINYINT(1) DEFAULT 0 COMMENT '是否已签署知情同意书',
    consent_date DATE COMMENT '知情同意签署日期',
    remarks TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_code (subject_code),
    INDEX idx_subject_name (last_name, first_name),
    INDEX idx_subject_birth_date (birth_date),
    INDEX idx_subject_status (status),
    INDEX idx_subject_enrollment_date (enrollment_date),
    INDEX idx_subject_sex (sex_code_id),
    INDEX idx_subject_nation (nation_code_id),
    INDEX idx_subject_enrollment_institution (enrollment_institution_id),
    INDEX idx_subject_id_card_hash (id_card_hash),
    CONSTRAINT fk_subject_sex FOREIGN KEY (sex_code_id) REFERENCES sex_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_nation FOREIGN KEY (nation_code_id) REFERENCES nation_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_education FOREIGN KEY (education_code_id) REFERENCES education_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_nationality FOREIGN KEY (nationality_code_id) REFERENCES nationality_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_marital FOREIGN KEY (marital_status_code_id) REFERENCES marital_status_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_blood_type FOREIGN KEY (blood_type_code_id) REFERENCES blood_type_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_insurance FOREIGN KEY (insurance_type_code_id) REFERENCES insurance_type_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_subject_enrollment_institution FOREIGN KEY (enrollment_institution_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受试者/研究对象表 - Subject';

-- --------------------------------------------------------------------------
-- 2.5 subject_cohort - 受试者-队列关联表 (多对多)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subject_cohort (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id BIGINT UNSIGNED NOT NULL COMMENT '受试者ID',
    cohort_id BIGINT UNSIGNED NOT NULL COMMENT '队列ID',
    enrollment_date DATE COMMENT '入队日期',
    exit_date DATE COMMENT '出队日期',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/completed/withdrawn',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_cohort (subject_id, cohort_id),
    INDEX idx_sc_cohort (cohort_id),
    INDEX idx_sc_status (status),
    INDEX idx_sc_enrollment_date (enrollment_date),
    CONSTRAINT fk_sc_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sc_cohort FOREIGN KEY (cohort_id) REFERENCES cohort(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受试者-队列关联表 - Subject Cohort';

-- --------------------------------------------------------------------------
-- 2.6 session - 访视/检查会话
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_code VARCHAR(50) NOT NULL COMMENT '会话编号',
    subject_id BIGINT UNSIGNED NOT NULL COMMENT '受试者ID',
    project_id BIGINT UNSIGNED COMMENT '项目ID',
    cohort_id BIGINT UNSIGNED COMMENT '队列ID',
    examination_type_code_id BIGINT UNSIGNED COMMENT '检查类型代码ID',
    session_date DATETIME NOT NULL COMMENT '会话日期时间',
    session_order INT COMMENT '访视次序 (基线=0, 第1次随访=1, 等)',
    examiner_name VARCHAR(100) COMMENT '检查者姓名',
    examiner_title VARCHAR(100) COMMENT '检查者职称',
    institution_id BIGINT UNSIGNED COMMENT '检查机构ID',
    status VARCHAR(20) NOT NULL DEFAULT 'scheduled' COMMENT '状态: scheduled/in_progress/completed/cancelled/missed',
    duration_minutes INT COMMENT '持续时长(分钟)',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_code (session_code),
    INDEX idx_session_subject (subject_id),
    INDEX idx_session_project (project_id),
    INDEX idx_session_cohort (cohort_id),
    INDEX idx_session_date (session_date),
    INDEX idx_session_status (status),
    INDEX idx_session_exam_type (examination_type_code_id),
    INDEX idx_session_institution (institution_id),
    CONSTRAINT fk_session_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_cohort FOREIGN KEY (cohort_id) REFERENCES cohort(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_exam_type FOREIGN KEY (examination_type_code_id) REFERENCES examination_type_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_institution FOREIGN KEY (institution_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访视/检查会话表 - Session';

-- --------------------------------------------------------------------------
-- 2.7 diagnosis - 诊断记录
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diagnosis (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id BIGINT UNSIGNED NOT NULL COMMENT '受试者ID',
    session_id BIGINT UNSIGNED COMMENT '关联会话ID',
    diagnosis_type_code_id BIGINT UNSIGNED COMMENT '诊断类型代码ID',
    icd10_code VARCHAR(20) NOT NULL COMMENT 'ICD-10诊断代码',
    icd10_name VARCHAR(300) NOT NULL COMMENT 'ICD-10诊断名称',
    diagnosis_name_cn VARCHAR(300) COMMENT '诊断中文名称',
    diagnosis_date DATE NOT NULL COMMENT '诊断日期',
    onset_date DATE COMMENT '发病日期',
    diagnosis_source VARCHAR(100) COMMENT '诊断来源: clinical/laboratory/imaging/pathological/combined',
    severity VARCHAR(50) COMMENT '严重程度: mild/moderate/severe',
    is_primary TINYINT(1) DEFAULT 1 COMMENT '是否为主要诊断',
    diagnosis_rank INT DEFAULT 1 COMMENT '诊断排序 (1=主要诊断)',
    clinician_name VARCHAR(100) COMMENT '诊断医师姓名',
    clinician_id BIGINT UNSIGNED COMMENT '诊断医师ID (关联user表)',
    institution_id BIGINT UNSIGNED COMMENT '诊断机构ID',
    confirmation_status VARCHAR(30) COMMENT '确认状态: provisional/confirmed/ruled_out',
    notes TEXT COMMENT '备注/诊断依据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_diagnosis_subject (subject_id),
    INDEX idx_diagnosis_session (session_id),
    INDEX idx_diagnosis_date (diagnosis_date),
    INDEX idx_diagnosis_icd10 (icd10_code),
    INDEX idx_diagnosis_type (diagnosis_type_code_id),
    INDEX idx_diagnosis_severity (severity),
    INDEX idx_diagnosis_is_primary (is_primary),
    CONSTRAINT fk_diagnosis_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_diagnosis_session FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE SET NULL,
    CONSTRAINT fk_diagnosis_type FOREIGN KEY (diagnosis_type_code_id) REFERENCES diagnosis_type_code(id) ON DELETE SET NULL,
    CONSTRAINT fk_diagnosis_institution FOREIGN KEY (institution_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断记录表 - Diagnosis';

-- --------------------------------------------------------------------------
-- 2.8 medication_history - 用药史
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medication_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id BIGINT UNSIGNED NOT NULL COMMENT '受试者ID',
    session_id BIGINT UNSIGNED COMMENT '关联会话ID',
    medication_name VARCHAR(200) NOT NULL COMMENT '药品通用名',
    medication_brand VARCHAR(200) COMMENT '药品商品名',
    atc_code VARCHAR(20) COMMENT 'ATC分类代码',
    medication_category VARCHAR(100) COMMENT '药物分类: antidepressant/antipsychotic/anxiolytic/cognitive_enhancer/etc',
    dosage_value DECIMAL(10,2) COMMENT '单次剂量数值',
    dosage_unit VARCHAR(30) COMMENT '剂量单位: mg/g/mL/IU',
    frequency VARCHAR(100) COMMENT '用药频率: qd/bid/tid/qid/prn/etc',
    route VARCHAR(50) COMMENT '给药途径: oral/intravenous/intramuscular/subcutaneous/transdermal',
    start_date DATE COMMENT '开始日期',
    end_date DATE COMMENT '结束日期',
    duration_days INT COMMENT '用药天数',
    is_current TINYINT(1) DEFAULT 0 COMMENT '是否为当前用药',
    indication TEXT COMMENT '用药指征/适应症',
    prescriber_name VARCHAR(100) COMMENT '处方医师姓名',
    prescriber_id BIGINT UNSIGNED COMMENT '处方医师ID (关联user表)',
    adherence VARCHAR(50) COMMENT '依从性: good/partial/poor/unknown',
    side_effects TEXT COMMENT '不良反应/副作用',
    effectiveness VARCHAR(50) COMMENT '疗效评价: effective/partially_effective/ineffective/unknown',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/discontinued/completed',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_med_subject (subject_id),
    INDEX idx_med_session (session_id),
    INDEX idx_med_name (medication_name),
    INDEX idx_med_atc (atc_code),
    INDEX idx_med_category (medication_category),
    INDEX idx_med_start_date (start_date),
    INDEX idx_med_is_current (is_current),
    INDEX idx_med_status (status),
    CONSTRAINT fk_med_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_med_session FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用药史表 - Medication History';

-- ============================================================================
-- Section 3: Laboratory Tables (实验室检查表)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 3.1 lab_test_panel - 检验项目组/面板
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_test_panel (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL COMMENT '面板代码',
    name VARCHAR(200) NOT NULL COMMENT '面板名称',
    name_zh VARCHAR(200) COMMENT '面板中文名称',
    category VARCHAR(100) COMMENT '分类: csf_biomarker/blood_chemistry/hematology/genetic/urinalysis/etc',
    sample_type_code_id BIGINT UNSIGNED COMMENT '样本类型代码ID',
    description TEXT COMMENT '描述',
    clinical_significance TEXT COMMENT '临床意义',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_panel_code (code),
    INDEX idx_panel_category (category),
    INDEX idx_panel_sample_type (sample_type_code_id),
    CONSTRAINT fk_panel_sample_type FOREIGN KEY (sample_type_code_id) REFERENCES sample_type_code(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验项目组/面板表 - Lab Test Panel';

-- --------------------------------------------------------------------------
-- 3.2 lab_test_item - 检验项目明细
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_test_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL COMMENT '项目代码',
    name VARCHAR(200) NOT NULL COMMENT '项目英文名称',
    name_zh VARCHAR(200) COMMENT '项目中文名称',
    loinc_code VARCHAR(20) COMMENT 'LOINC代码',
    category VARCHAR(100) COMMENT '分类',
    sample_type_code_id BIGINT UNSIGNED COMMENT '样本类型代码ID',
    unit VARCHAR(50) COMMENT '常用单位',
    decimal_places INT DEFAULT 2 COMMENT '小数位数',
    reference_range_low DECIMAL(15,4) COMMENT '参考范围下限',
    reference_range_high DECIMAL(15,4) COMMENT '参考范围上限',
    reference_range_text VARCHAR(200) COMMENT '参考范围文本描述',
    abnormal_direction VARCHAR(10) COMMENT '异常方向: high/low/both',
    description TEXT COMMENT '描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_code (code),
    INDEX idx_item_loinc (loinc_code),
    INDEX idx_item_category (category),
    INDEX idx_item_sample_type (sample_type_code_id),
    CONSTRAINT fk_item_sample_type FOREIGN KEY (sample_type_code_id) REFERENCES sample_type_code(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验项目明细表 - Lab Test Item';

-- --------------------------------------------------------------------------
-- 3.3 lab_panel_item - 面板-项目关联表 (多对多)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_panel_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    panel_id BIGINT UNSIGNED NOT NULL COMMENT '面板ID',
    item_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
    sort_order INT DEFAULT 0 COMMENT '面板内排序',
    is_required TINYINT(1) DEFAULT 0 COMMENT '是否必查项',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_panel_item (panel_id, item_id),
    INDEX idx_lpi_item (item_id),
    CONSTRAINT fk_lpi_panel FOREIGN KEY (panel_id) REFERENCES lab_test_panel(id) ON DELETE CASCADE,
    CONSTRAINT fk_lpi_item FOREIGN KEY (item_id) REFERENCES lab_test_item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面板-项目关联表 - Lab Panel Item';

-- --------------------------------------------------------------------------
-- 3.4 lab_result - 检验结果
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id BIGINT UNSIGNED NOT NULL COMMENT '受试者ID',
    session_id BIGINT UNSIGNED COMMENT '关联会话ID',
    lab_test_item_id BIGINT UNSIGNED NOT NULL COMMENT '检验项目ID',
    result_value DECIMAL(15,4) COMMENT '结果数值',
    result_text VARCHAR(500) COMMENT '结果文本 (定性结果)',
    unit VARCHAR(50) COMMENT '单位',
    reference_range_low DECIMAL(15,4) COMMENT '参考范围下限',
    reference_range_high DECIMAL(15,4) COMMENT '参考范围上限',
    is_abnormal TINYINT(1) DEFAULT 0 COMMENT '是否异常',
    abnormal_flag VARCHAR(10) COMMENT '异常标记: H/L/HH/LL/A',
    test_date DATETIME COMMENT '检验日期',
    reported_date DATETIME COMMENT '报告日期',
    sample_id VARCHAR(100) COMMENT '样本ID/条码',
    sample_collection_date DATETIME COMMENT '样本采集日期',
    lab_name VARCHAR(200) COMMENT '检验实验室名称',
    method VARCHAR(200) COMMENT '检验方法',
    instrument VARCHAR(200) COMMENT '检验仪器',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_lr_subject (subject_id),
    INDEX idx_lr_session (session_id),
    INDEX idx_lr_item (lab_test_item_id),
    INDEX idx_lr_test_date (test_date),
    INDEX idx_lr_sample_id (sample_id),
    INDEX idx_lr_is_abnormal (is_abnormal),
    CONSTRAINT fk_lr_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_session FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE SET NULL,
    CONSTRAINT fk_lr_item FOREIGN KEY (lab_test_item_id) REFERENCES lab_test_item(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验结果表 - Lab Result';

-- ============================================================================
-- Section 4: Clinical Assessment Tables (临床评估表)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 4.1 scale_instrument - 量表/评估工具
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scale_instrument (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL COMMENT '量表代码',
    name VARCHAR(200) NOT NULL COMMENT '量表英文名称',
    name_zh VARCHAR(200) COMMENT '量表中文名称',
    abbreviation VARCHAR(20) COMMENT '缩写',
    category VARCHAR(100) COMMENT '分类: cognitive/mood/anxiety/functional/behavioral/global',
    description TEXT COMMENT '量表描述',
    version VARCHAR(50) COMMENT '版本',
    total_score_min DECIMAL(8,2) COMMENT '总分最小值',
    total_score_max DECIMAL(8,2) COMMENT '总分最大值',
    cutoff_score DECIMAL(8,2) COMMENT '截断分/阈值',
    cutoff_interpretation VARCHAR(300) COMMENT '截断分解释',
    administration_time_min INT COMMENT '完成时间(分钟)',
    instruction TEXT COMMENT '使用说明/指导语',
    reference_url VARCHAR(500) COMMENT '参考文献链接',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scale_code (code),
    INDEX idx_scale_category (category),
    INDEX idx_scale_abbreviation (abbreviation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表/评估工具表 - Scale Instrument';

-- --------------------------------------------------------------------------
-- 4.2 imaging_modality - 影像模态
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS imaging_modality (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL COMMENT '模态代码',
    name VARCHAR(100) NOT NULL COMMENT '模态英文名称',
    name_zh VARCHAR(100) COMMENT '模态中文名称',
    abbreviation VARCHAR(20) COMMENT '缩写',
    category VARCHAR(50) COMMENT '分类: structural/functional/diffusion/perfusion/metabolic/electrophysiology',
    description TEXT COMMENT '描述',
    requires_contrast TINYINT(1) DEFAULT 0 COMMENT '是否需要造影剂',
    typical_duration_min INT COMMENT '典型扫描时长(分钟)',
    spatial_resolution VARCHAR(100) COMMENT '空间分辨率',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_modality_code (code),
    INDEX idx_modality_category (category),
    INDEX idx_modality_abbreviation (abbreviation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影像模态表 - Imaging Modality';

-- ============================================================================
-- Section 5: User / Role / Permission Tables (用户/角色/权限表)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 5.1 user - 系统用户
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    email VARCHAR(200) COMMENT '邮箱',
    phone VARCHAR(30) COMMENT '电话',
    first_name VARCHAR(100) COMMENT '名',
    last_name VARCHAR(100) COMMENT '姓',
    title VARCHAR(100) COMMENT '职称/头衔',
    institution_id BIGINT UNSIGNED COMMENT '所属机构ID',
    department VARCHAR(200) COMMENT '科室/部门',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_locked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定',
    last_login_at DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    password_changed_at DATETIME COMMENT '密码最后修改时间',
    must_change_password TINYINT(1) DEFAULT 0 COMMENT '是否必须修改密码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    INDEX idx_user_institution (institution_id),
    INDEX idx_user_is_active (is_active),
    CONSTRAINT fk_user_institution FOREIGN KEY (institution_id) REFERENCES institution(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表 - User';

-- --------------------------------------------------------------------------
-- 5.2 role - 角色
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL COMMENT '角色代码',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    name_zh VARCHAR(100) COMMENT '角色中文名称',
    description VARCHAR(500) COMMENT '角色描述',
    is_system TINYINT(1) DEFAULT 0 COMMENT '是否系统内置角色',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表 - Role';

-- --------------------------------------------------------------------------
-- 5.3 user_role - 用户-角色关联表 (多对多)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    project_id BIGINT UNSIGNED COMMENT '项目范围 (空=全局角色)',
    granted_by BIGINT UNSIGNED COMMENT '授权人ID',
    granted_at DATETIME COMMENT '授权时间',
    expires_at DATETIME COMMENT '过期时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_project (user_id, role_id, project_id),
    INDEX idx_ur_role (role_id),
    INDEX idx_ur_project (project_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表 - User Role';

-- --------------------------------------------------------------------------
-- 5.4 permission - 权限
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL COMMENT '权限代码',
    name VARCHAR(200) NOT NULL COMMENT '权限名称',
    name_zh VARCHAR(200) COMMENT '权限中文名称',
    resource VARCHAR(100) COMMENT '资源: user/subject/session/diagnosis/lab/project/cohort/report/system',
    action VARCHAR(50) COMMENT '操作: create/read/update/delete/export/approve/manage',
    description VARCHAR(500) COMMENT '权限描述',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code),
    INDEX idx_permission_resource (resource),
    INDEX idx_permission_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表 - Permission';

-- --------------------------------------------------------------------------
-- 5.5 role_permission - 角色-权限关联表 (多对多)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_rp_permission (permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表 - Role Permission';

-- ============================================================================
-- Section 6: Seed Data (初始化数据)
-- ============================================================================

-- --------------------------------------------------------------------------
-- 6.1 sex_code (GB/T 2261.1)
-- --------------------------------------------------------------------------
INSERT INTO sex_code (code, name, description, sort_order) VALUES
('0', '未知的性别', 'Unknown', 1),
('1', '男性', 'Male', 2),
('2', '女性', 'Female', 3),
('9', '未说明的性别', 'Unspecified', 4);

-- --------------------------------------------------------------------------
-- 6.2 nation_code (GB/T 3304 - 主要民族)
-- --------------------------------------------------------------------------
INSERT INTO nation_code (code, name, pinyin, sort_order) VALUES
('01', '汉族', 'Han', 1),
('02', '蒙古族', 'Menggu', 2),
('03', '回族', 'Hui', 3),
('04', '藏族', 'Zang', 4),
('05', '维吾尔族', 'Uygur', 5),
('06', '苗族', 'Miao', 6),
('07', '彝族', 'Yi', 7),
('08', '壮族', 'Zhuang', 8),
('09', '布依族', 'Buyei', 9),
('10', '朝鲜族', 'Chosen', 10),
('11', '满族', 'Man', 11),
('12', '侗族', 'Dong', 12),
('13', '瑶族', 'Yao', 13),
('14', '白族', 'Bai', 14),
('15', '土家族', 'Tujia', 15);

-- --------------------------------------------------------------------------
-- 6.3 education_code (GB/T 4658)
-- --------------------------------------------------------------------------
INSERT INTO education_code (code, name, level, sort_order) VALUES
('10', '文盲或半文盲', 0, 1),
('20', '小学', 1, 2),
('30', '初中', 2, 3),
('40', '高中/中专/技校', 3, 4),
('50', '大专', 4, 5),
('60', '大学本科', 5, 6),
('70', '硕士研究生', 6, 7),
('80', '博士研究生', 7, 8),
('90', '其他', 9, 9);

-- --------------------------------------------------------------------------
-- 6.4 nationality_code (GB/T 2659 - 常用国籍)
-- --------------------------------------------------------------------------
INSERT INTO nationality_code (code, name, name_zh, sort_order) VALUES
('CHN', 'China', '中国', 1),
('USA', 'United States', '美国', 2),
('GBR', 'United Kingdom', '英国', 3),
('JPN', 'Japan', '日本', 4),
('KOR', 'Korea, Republic of', '韩国', 5),
('DEU', 'Germany', '德国', 6),
('FRA', 'France', '法国', 7),
('CAN', 'Canada', '加拿大', 8),
('AUS', 'Australia', '澳大利亚', 9),
('SGP', 'Singapore', '新加坡', 10);

-- --------------------------------------------------------------------------
-- 6.5 marital_status_code
-- --------------------------------------------------------------------------
INSERT INTO marital_status_code (code, name, description, sort_order) VALUES
('1', '未婚', 'Unmarried', 1),
('2', '已婚', 'Married', 2),
('3', '离异', 'Divorced', 3),
('4', '丧偶', 'Widowed', 4),
('5', '再婚', 'Remarried', 5),
('9', '其他', 'Other', 9);

-- --------------------------------------------------------------------------
-- 6.6 blood_type_code
-- --------------------------------------------------------------------------
INSERT INTO blood_type_code (code, name, abo_type, rh_type, sort_order) VALUES
('A+', 'A型 Rh阳性', 'A', '+', 1),
('A-', 'A型 Rh阴性', 'A', '-', 2),
('B+', 'B型 Rh阳性', 'B', '+', 3),
('B-', 'B型 Rh阴性', 'B', '-', 4),
('AB+', 'AB型 Rh阳性', 'AB', '+', 5),
('AB-', 'AB型 Rh阴性', 'AB', '-', 6),
('O+', 'O型 Rh阳性', 'O', '+', 7),
('O-', 'O型 Rh阴性', 'O', '-', 8),
('UNK', '未知', NULL, NULL, 9);

-- --------------------------------------------------------------------------
-- 6.7 insurance_type_code
-- --------------------------------------------------------------------------
INSERT INTO insurance_type_code (code, name, description, sort_order) VALUES
('UEBMI', '城镇职工基本医疗保险', 'Urban Employee Basic Medical Insurance', 1),
('URBMI', '城镇居民基本医疗保险', 'Urban Resident Basic Medical Insurance', 2),
('NRCMS', '新型农村合作医疗', 'New Rural Cooperative Medical Scheme', 3),
('CIS', '商业医疗保险', 'Commercial Insurance', 4),
('MA', '医疗救助', 'Medical Assistance', 5),
('SELF', '自费', 'Self-pay', 6),
('OTH', '其他', 'Other', 9);

-- --------------------------------------------------------------------------
-- 6.8 examination_type_code
-- --------------------------------------------------------------------------
INSERT INTO examination_type_code (code, name, description, sort_order) VALUES
('SCREEN', '筛查', 'Screening visit', 1),
('BASELINE', '基线', 'Baseline visit', 2),
('FU_1M', '第1个月随访', '1-month follow-up', 3),
('FU_3M', '第3个月随访', '3-month follow-up', 4),
('FU_6M', '第6个月随访', '6-month follow-up', 5),
('FU_12M', '第12个月随访', '12-month follow-up', 6),
('FU_24M', '第24个月随访', '24-month follow-up', 7),
('UNSCHED', '非计划访视', 'Unscheduled visit', 8),
('CLOSE_OUT', '结题访视', 'Close-out visit', 9),
('ADVERSE', '不良反应访视', 'Adverse event visit', 10);

-- --------------------------------------------------------------------------
-- 6.9 sample_type_code
-- --------------------------------------------------------------------------
INSERT INTO sample_type_code (code, name, description, sort_order) VALUES
('BLOOD', '全血', 'Whole blood', 1),
('SERUM', '血清', 'Serum', 2),
('PLASMA', '血浆', 'Plasma', 3),
('CSF', '脑脊液', 'Cerebrospinal fluid', 4),
('URINE', '尿液', 'Urine', 5),
('SALIVA', '唾液', 'Saliva', 6),
('TISSUE', '组织', 'Tissue', 7),
('BUCCAL', '口腔拭子', 'Buccal swab', 8),
('STOOL', '粪便', 'Stool', 9);

-- --------------------------------------------------------------------------
-- 6.10 diagnosis_type_code
-- --------------------------------------------------------------------------
INSERT INTO diagnosis_type_code (code, name, description, sort_order) VALUES
('ADMISSION', '入院诊断', 'Admission diagnosis', 1),
('CLINICAL', '临床诊断', 'Clinical diagnosis', 2),
('LAB', '实验室诊断', 'Laboratory diagnosis', 3),
('IMAGING', '影像学诊断', 'Imaging diagnosis', 4),
('PATHOLOGY', '病理诊断', 'Pathological diagnosis', 5),
('DISCHARGE', '出院诊断', 'Discharge diagnosis', 6),
('DIFFERENTIAL', '鉴别诊断', 'Differential diagnosis', 7),
('FINAL', '最终诊断', 'Final diagnosis', 8);

-- --------------------------------------------------------------------------
-- 6.11 Roles (5 roles)
-- --------------------------------------------------------------------------
INSERT INTO role (code, name, name_zh, description, is_system, sort_order) VALUES
('admin', 'System Administrator', '系统管理员', 'Full system access and configuration', 1, 1),
('pi', 'Principal Investigator', '主要研究者', 'Project management, subject oversight, data access', 1, 2),
('clinician', 'Clinician', '临床医生', 'Subject enrollment, assessment, diagnosis, data entry', 1, 3),
('data_analyst', 'Data Analyst', '数据分析师', 'Data query, statistical analysis, report generation', 1, 4),
('viewer', 'Viewer', '观察者', 'Read-only access to authorized data', 1, 5);

-- --------------------------------------------------------------------------
-- 6.12 Permissions (~22 permissions)
-- --------------------------------------------------------------------------
INSERT INTO permission (code, name, name_zh, resource, action, description, sort_order) VALUES
('user:create', 'Create User', '创建用户', 'user', 'create', 'Create new system users', 1),
('user:read', 'View User', '查看用户', 'user', 'read', 'View user information', 2),
('user:update', 'Update User', '更新用户', 'user', 'update', 'Update user information', 3),
('user:delete', 'Delete User', '删除用户', 'user', 'delete', 'Delete/deactivate users', 4),
('subject:create', 'Create Subject', '创建受试者', 'subject', 'create', 'Register new subjects', 5),
('subject:read', 'View Subject', '查看受试者', 'subject', 'read', 'View subject information', 6),
('subject:update', 'Update Subject', '更新受试者', 'subject', 'update', 'Update subject information', 7),
('subject:delete', 'Delete Subject', '删除受试者', 'subject', 'delete', 'Delete subject records', 8),
('session:create', 'Create Session', '创建访视', 'session', 'create', 'Create assessment sessions', 9),
('session:read', 'View Session', '查看访视', 'session', 'read', 'View session data', 10),
('session:update', 'Update Session', '更新访视', 'session', 'update', 'Update session records', 11),
('session:delete', 'Delete Session', '删除访视', 'session', 'delete', 'Delete session records', 12),
('diagnosis:create', 'Create Diagnosis', '创建诊断', 'diagnosis', 'create', 'Record new diagnoses', 13),
('diagnosis:read', 'View Diagnosis', '查看诊断', 'diagnosis', 'read', 'View diagnosis records', 14),
('diagnosis:update', 'Update Diagnosis', '更新诊断', 'diagnosis', 'update', 'Update diagnosis records', 15),
('diagnosis:delete', 'Delete Diagnosis', '删除诊断', 'diagnosis', 'delete', 'Delete diagnosis records', 16),
('lab:create', 'Create Lab Result', '创建检验结果', 'lab', 'create', 'Enter lab test results', 17),
('lab:read', 'View Lab Result', '查看检验结果', 'lab', 'read', 'View lab test results', 18),
('lab:update', 'Update Lab Result', '更新检验结果', 'lab', 'update', 'Update lab test results', 19),
('lab:delete', 'Delete Lab Result', '删除检验结果', 'lab', 'delete', 'Delete lab test results', 20),
('project:manage', 'Manage Project', '管理项目', 'project', 'manage', 'Configure and manage projects', 21),
('cohort:manage', 'Manage Cohort', '管理队列', 'cohort', 'manage', 'Create and manage cohorts', 22),
('data:export', 'Export Data', '导出数据', 'report', 'export', 'Export research data', 23),
('report:view', 'View Reports', '查看报表', 'report', 'read', 'View system reports and dashboards', 24),
('audit:view', 'View Audit Log', '查看审计日志', 'system', 'read', 'View system audit trail', 25),
('system:configure', 'System Configuration', '系统配置', 'system', 'manage', 'Manage system settings', 26);

-- --------------------------------------------------------------------------
-- 6.13 Role-Permission assignments
-- --------------------------------------------------------------------------

-- admin: ALL permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r CROSS JOIN permission p
WHERE r.code = 'admin';

-- pi: project management + full read + subject/session/diagnosis/lab CRUD + export + reports + audit
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'pi'
  AND p.code IN (
    'project:manage', 'cohort:manage',
    'subject:create', 'subject:read', 'subject:update',
    'session:create', 'session:read', 'session:update',
    'diagnosis:create', 'diagnosis:read', 'diagnosis:update',
    'lab:create', 'lab:read', 'lab:update',
    'data:export', 'report:view', 'audit:view',
    'user:read'
  );

-- clinician: subject/session/diagnosis/lab CRUD + read-only reports
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'clinician'
  AND p.code IN (
    'subject:create', 'subject:read', 'subject:update',
    'session:create', 'session:read', 'session:update',
    'diagnosis:create', 'diagnosis:read', 'diagnosis:update',
    'lab:create', 'lab:read', 'lab:update',
    'report:view'
  );

-- data_analyst: read all + export + reports + audit
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'data_analyst'
  AND p.code IN (
    'subject:read', 'session:read', 'diagnosis:read', 'lab:read',
    'data:export', 'report:view', 'audit:view', 'user:read'
  );

-- viewer: read-only on subject/session/diagnosis/lab + reports
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'viewer'
  AND p.code IN (
    'subject:read', 'session:read', 'diagnosis:read', 'lab:read',
    'report:view'
  );

-- --------------------------------------------------------------------------
-- 6.14 Scale Instruments (6 instruments)
-- --------------------------------------------------------------------------
INSERT INTO scale_instrument (code, name, name_zh, abbreviation, category, description, version, total_score_min, total_score_max, cutoff_score, cutoff_interpretation, administration_time_min, sort_order) VALUES
('MMSE', 'Mini-Mental State Examination', '简易智能状态检查', 'MMSE', 'cognitive',
 'Widely used screening tool for cognitive impairment. Assesses orientation, registration, attention, recall, and language.',
 '2nd Edition', 0.00, 30.00, 24.00, 'Score < 24 suggests cognitive impairment; 18-23 = mild, 10-17 = moderate, <10 = severe',
 10, 1),
('MOCA', 'Montreal Cognitive Assessment', '蒙特利尔认知评估', 'MoCA', 'cognitive',
 'Cognitive screening instrument for mild cognitive impairment (MCI). Assesses visuospatial/executive, naming, memory, attention, language, abstraction, delayed recall, orientation.',
 'Version 8.1', 0.00, 30.00, 26.00, 'Score < 26 suggests cognitive impairment; 1 point correction for education <= 12 years',
 10, 2),
('HAMD', 'Hamilton Depression Rating Scale', '汉密尔顿抑郁量表', 'HAMD', 'mood',
 'Clinician-administered depression assessment scale. Evaluates severity of depressive symptoms.',
 '17-item version', 0.00, 52.00, 7.00, '0-7 = normal, 8-16 = mild, 17-23 = moderate, >=24 = severe depression',
 20, 3),
('HAMA', 'Hamilton Anxiety Rating Scale', '汉密尔顿焦虑量表', 'HAMA', 'anxiety',
 'Clinician-administered scale to quantify severity of anxiety symptoms. Covers psychic and somatic anxiety.',
 'Standard', 0.00, 56.00, 7.00, '0-7 = no/minimal anxiety, 8-14 = mild, 15-23 = moderate, >=24 = severe anxiety',
 15, 4),
('CDR', 'Clinical Dementia Rating', '临床痴呆评定量表', 'CDR', 'global',
 'Global rating scale for staging dementia severity. Assesses memory, orientation, judgment, community affairs, home/hobbies, personal care.',
 'Standard', 0.00, 3.00, 0.50, '0 = no dementia, 0.5 = questionable, 1 = mild, 2 = moderate, 3 = severe',
 30, 5),
('NPI', 'Neuropsychiatric Inventory', '神经精神量表', 'NPI', 'behavioral',
 'Assesses neuropsychiatric symptoms in dementia: delusions, hallucinations, agitation, depression, anxiety, euphoria, apathy, disinhibition, irritability, aberrant motor behavior.',
 'Standard', 0.00, 144.00, NULL, 'Higher scores indicate greater behavioral disturbance; frequency x severity for each domain',
 20, 6),
('ADL', 'Activities of Daily Living Scale', '日常生活能力量表', 'ADL', 'functional',
 'Assesses functional capacity in basic and instrumental activities of daily living in dementia patients.',
 'Standard', 14.00, 56.00, 16.00, 'Score > 16 indicates functional impairment; higher scores indicate greater disability',
 15, 7);

-- --------------------------------------------------------------------------
-- 6.15 Imaging Modalities (8 modalities)
-- --------------------------------------------------------------------------
INSERT INTO imaging_modality (code, name, name_zh, abbreviation, category, description, requires_contrast, typical_duration_min, spatial_resolution, sort_order) VALUES
('MRI_T1', 'T1-weighted MRI', 'T1加权磁共振', 'T1w', 'structural',
 'High-resolution anatomical imaging. Excellent gray-white matter contrast. Core structural sequence.', 0, 5, '1 mm isotropic', 1),
('MRI_T2', 'T2-weighted MRI', 'T2加权磁共振', 'T2w', 'structural',
 'Sensitive to tissue water content. Useful for detecting white matter hyperintensities and lesions.', 0, 5, '1 mm isotropic', 2),
('MRI_FLAIR', 'Fluid-Attenuated Inversion Recovery', '液体衰减反转恢复', 'FLAIR', 'structural',
 'Suppresses CSF signal. Excellent for periventricular and subcortical white matter lesion detection.', 0, 5, '1 mm isotropic', 3),
('DTI', 'Diffusion Tensor Imaging', '弥散张量成像', 'DTI', 'diffusion',
 'Maps white matter fiber tracts and microstructural integrity. Produces FA, MD, AD, RD maps.', 0, 10, '2 mm isotropic', 4),
('RS_FMRI', 'Resting-State fMRI', '静息态功能磁共振', 'rs-fMRI', 'functional',
 'Measures spontaneous brain activity and functional connectivity networks at rest.', 0, 8, '3 mm isotropic', 5),
('TASK_FMRI', 'Task-based fMRI', '任务态功能磁共振', 'task-fMRI', 'functional',
 'Measures brain activation patterns during cognitive tasks/paradigms.', 0, 15, '3 mm isotropic', 6),
('CT', 'Computed Tomography', '计算机断层扫描', 'CT', 'structural',
 'X-ray based tomographic imaging. Fast acquisition. Useful for acute hemorrhage, atrophy assessment.', 0, 2, '0.5 mm', 7),
('PET_AMYLOID', 'Amyloid PET', '淀粉样蛋白PET', 'Amyloid-PET', 'metabolic',
 'Positron emission tomography using amyloid-binding tracers (e.g., PiB, Florbetapir) to detect cerebral amyloid deposition.',
 1, 20, '4-6 mm FWHM', 8);

-- --------------------------------------------------------------------------
-- 6.16 Lab Test Panels (16+ panels)
-- --------------------------------------------------------------------------

-- Get sample type IDs for reference
-- BLOOD -> sample_type_code WHERE code='BLOOD'
-- SERUM -> sample_type_code WHERE code='SERUM'
-- PLASMA -> sample_type_code WHERE code='PLASMA'
-- CSF -> sample_type_code WHERE code='CSF'
-- URINE -> sample_type_code WHERE code='URINE'

-- Panels
INSERT INTO lab_test_panel (code, name, name_zh, category, description, sort_order) VALUES
('PANEL_CSF', 'CSF Biomarkers Panel', '脑脊液生物标志物组', 'csf_biomarker', 'Core Alzheimer disease CSF biomarkers: Aβ42, Aβ40, p-tau181, t-tau, NfL', 1),
('PANEL_GLUCOSE', 'Blood Glucose Panel', '血糖组', 'blood_chemistry', 'Fasting glucose and HbA1c for diabetes screening', 2),
('PANEL_LIPID', 'Lipid Panel', '血脂组', 'blood_chemistry', 'Total cholesterol, HDL, LDL, triglycerides for cardiovascular risk', 3),
('PANEL_CBC', 'Complete Blood Count', '血常规', 'hematology', 'WBC, RBC, hemoglobin, hematocrit, platelets', 4),
('PANEL_LIVER', 'Liver Function Panel', '肝功能组', 'blood_chemistry', 'ALT, AST, ALP, total bilirubin, albumin', 5),
('PANEL_KIDNEY', 'Kidney Function Panel', '肾功能组', 'blood_chemistry', 'Creatinine, BUN, eGFR, uric acid', 6),
('PANEL_APOE', 'ApoE Genotype', 'ApoE基因型', 'genetic', 'Apolipoprotein E genotype determination (ε2/ε3/ε4 alleles)', 7),
('PANEL_THYROID', 'Thyroid Function Panel', '甲状腺功能组', 'blood_chemistry', 'TSH, free T3, free T4 for thyroid disorder screening', 8),
('PANEL_VITAMIN', 'Vitamin B12 / Folate Panel', '维生素B12/叶酸组', 'blood_chemistry', 'Vitamin B12 and folate levels for cognitive impairment differential', 9),
('PANEL_INFLAMM', 'Inflammatory Markers Panel', '炎症标志物组', 'blood_chemistry', 'CRP, ESR for systemic inflammation assessment', 10),
('PANEL_COAG', 'Coagulation Panel', '凝血功能组', 'hematology', 'PT, APTT, INR, fibrinogen', 11),
('PANEL_ELECTROLYTE', 'Electrolyte Panel', '电解质组', 'blood_chemistry', 'Sodium, potassium, chloride, calcium, magnesium, phosphorus', 12),
('PANEL_IRON', 'Iron Studies Panel', '铁代谢组', 'blood_chemistry', 'Serum iron, ferritin, transferrin, TIBC for anemia workup', 13),
('PANEL_URINALYSIS', 'Urinalysis Panel', '尿液分析组', 'urinalysis', 'Routine urinalysis parameters', 14),
('PANEL_HOMOCYSTEINE', 'Homocysteine Panel', '同型半胱氨酸组', 'blood_chemistry', 'Homocysteine level as cardiovascular and cognitive risk marker', 15),
('PANEL_CARDIAC', 'Cardiac Markers Panel', '心脏标志物组', 'blood_chemistry', 'Troponin, CK-MB, BNP for cardiac evaluation', 16);

-- --------------------------------------------------------------------------
-- 6.17 Lab Test Items (with LOINC codes)
-- --------------------------------------------------------------------------

-- CSF Biomarkers
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('CSF_AB42', 'Amyloid Beta 42', 'β-淀粉样蛋白42', '57905-1', 'csf_biomarker', 'pg/mL', 1, 1),
('CSF_AB40', 'Amyloid Beta 40', 'β-淀粉样蛋白40', '57903-6', 'csf_biomarker', 'pg/mL', 1, 2),
('CSF_PTAU181', 'Phosphorylated Tau 181', '磷酸化Tau蛋白181', '57906-9', 'csf_biomarker', 'pg/mL', 1, 3),
('CSF_TTAU', 'Total Tau', '总Tau蛋白', '57908-5', 'csf_biomarker', 'pg/mL', 1, 4),
('CSF_NFL', 'Neurofilament Light Chain', '神经丝轻链蛋白', '94692-5', 'csf_biomarker', 'pg/mL', 1, 5),
('CSF_AB42_AB40', 'Aβ42/Aβ40 Ratio', 'Aβ42/Aβ40比值', '57902-8', 'csf_biomarker', 'ratio', 3, 6);

-- Blood Glucose
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('GLU_FASTING', 'Fasting Glucose', '空腹血糖', '1558-6', 'blood_chemistry', 'mmol/L', 2, 10),
('GLU_HBA1C', 'Hemoglobin A1c', '糖化血红蛋白', '4548-4', 'blood_chemistry', '%', 1, 11);

-- Lipid Panel
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('LIPID_TC', 'Total Cholesterol', '总胆固醇', '2093-3', 'blood_chemistry', 'mmol/L', 2, 20),
('LIPID_HDL', 'HDL Cholesterol', '高密度脂蛋白胆固醇', '2085-9', 'blood_chemistry', 'mmol/L', 2, 21),
('LIPID_LDL', 'LDL Cholesterol', '低密度脂蛋白胆固醇', '2089-1', 'blood_chemistry', 'mmol/L', 2, 22),
('LIPID_TG', 'Triglycerides', '甘油三酯', '2571-8', 'blood_chemistry', 'mmol/L', 2, 23);

-- CBC
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('CBC_WBC', 'White Blood Cell Count', '白细胞计数', '6690-2', 'hematology', 'x10^9/L', 2, 30),
('CBC_RBC', 'Red Blood Cell Count', '红细胞计数', '789-8', 'hematology', 'x10^12/L', 2, 31),
('CBC_HGB', 'Hemoglobin', '血红蛋白', '718-7', 'hematology', 'g/L', 1, 32),
('CBC_HCT', 'Hematocrit', '红细胞比容', '4544-3', 'hematology', '%', 1, 33),
('CBC_PLT', 'Platelet Count', '血小板计数', '777-3', 'hematology', 'x10^9/L', 1, 34),
('CBC_MCV', 'Mean Corpuscular Volume', '平均红细胞体积', '787-2', 'hematology', 'fL', 1, 35);

-- Liver Function
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('LIVER_ALT', 'Alanine Aminotransferase', '谷丙转氨酶', '1742-6', 'blood_chemistry', 'U/L', 1, 40),
('LIVER_AST', 'Aspartate Aminotransferase', '谷草转氨酶', '1920-8', 'blood_chemistry', 'U/L', 1, 41),
('LIVER_ALP', 'Alkaline Phosphatase', '碱性磷酸酶', '6768-6', 'blood_chemistry', 'U/L', 1, 42),
('LIVER_TBIL', 'Total Bilirubin', '总胆红素', '1975-2', 'blood_chemistry', 'μmol/L', 1, 43),
('LIVER_ALB', 'Albumin', '白蛋白', '1751-7', 'blood_chemistry', 'g/L', 1, 44);

-- Kidney Function
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('KIDNEY_CREAT', 'Creatinine', '肌酐', '2160-0', 'blood_chemistry', 'μmol/L', 1, 50),
('KIDNEY_BUN', 'Blood Urea Nitrogen', '尿素氮', '3094-0', 'blood_chemistry', 'mmol/L', 2, 51),
('KIDNEY_EGFR', 'Estimated GFR', '估算肾小球滤过率', '33914-3', 'blood_chemistry', 'mL/min/1.73m²', 1, 52),
('KIDNEY_UA', 'Uric Acid', '尿酸', '3084-1', 'blood_chemistry', 'μmol/L', 1, 53);

-- ApoE Genotype
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('APOE_GENOTYPE', 'ApoE Genotype', 'ApoE基因型', '57907-7', 'genetic', NULL, 0, 60);

-- Thyroid Function
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('THYROID_TSH', 'Thyroid Stimulating Hormone', '促甲状腺激素', '3016-3', 'blood_chemistry', 'mIU/L', 2, 70),
('THYROID_FT3', 'Free T3', '游离三碘甲状腺原氨酸', '3051-0', 'blood_chemistry', 'pmol/L', 1, 71),
('THYROID_FT4', 'Free T4', '游离甲状腺素', '3024-7', 'blood_chemistry', 'pmol/L', 1, 72);

-- Vitamin B12 / Folate
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('VIT_B12', 'Vitamin B12', '维生素B12', '2132-9', 'blood_chemistry', 'pmol/L', 1, 80),
('VIT_FOLATE', 'Folate', '叶酸', '2284-8', 'blood_chemistry', 'nmol/L', 1, 81);

-- Inflammatory Markers
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('INFLAMM_CRP', 'C-Reactive Protein', 'C反应蛋白', '1988-5', 'blood_chemistry', 'mg/L', 2, 90),
('INFLAMM_ESR', 'Erythrocyte Sedimentation Rate', '红细胞沉降率', '4537-7', 'hematology', 'mm/h', 1, 91);

-- Coagulation
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('COAG_PT', 'Prothrombin Time', '凝血酶原时间', '5902-2', 'hematology', 's', 1, 100),
('COAG_APTT', 'Activated Partial Thromboplastin Time', '活化部分凝血活酶时间', '3173-2', 'hematology', 's', 1, 101),
('COAG_INR', 'International Normalized Ratio', '国际标准化比值', '34714-6', 'hematology', NULL, 2, 102),
('COAG_FIB', 'Fibrinogen', '纤维蛋白原', '3255-7', 'hematology', 'g/L', 1, 103);

-- Electrolytes
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('ELEC_NA', 'Sodium', '钠', '2951-2', 'blood_chemistry', 'mmol/L', 1, 110),
('ELEC_K', 'Potassium', '钾', '2823-3', 'blood_chemistry', 'mmol/L', 1, 111),
('ELEC_CL', 'Chloride', '氯', '2075-0', 'blood_chemistry', 'mmol/L', 1, 112),
('ELEC_CA', 'Calcium', '钙', '17861-6', 'blood_chemistry', 'mmol/L', 2, 113),
('ELEC_MG', 'Magnesium', '镁', '2601-3', 'blood_chemistry', 'mmol/L', 2, 114),
('ELEC_P', 'Phosphorus', '磷', '2777-1', 'blood_chemistry', 'mmol/L', 2, 115);

-- Iron Studies
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('IRON_SI', 'Serum Iron', '血清铁', '2498-4', 'blood_chemistry', 'μmol/L', 1, 120),
('IRON_FERRITIN', 'Ferritin', '铁蛋白', '2276-4', 'blood_chemistry', 'μg/L', 1, 121),
('IRON_TRF', 'Transferrin', '转铁蛋白', '3034-6', 'blood_chemistry', 'g/L', 1, 122),
('IRON_TIBC', 'Total Iron Binding Capacity', '总铁结合力', '2500-7', 'blood_chemistry', 'μmol/L', 1, 123);

-- Urinalysis
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('UA_PH', 'Urine pH', '尿液pH', '2756-5', 'urinalysis', NULL, 1, 130),
('UA_SG', 'Urine Specific Gravity', '尿比重', '5811-5', 'urinalysis', NULL, 3, 131),
('UA_PROTEIN', 'Urine Protein', '尿蛋白', '2888-6', 'urinalysis', 'g/L', 2, 132),
('UA_GLUCOSE', 'Urine Glucose', '尿糖', '25428-4', 'urinalysis', 'mmol/L', 1, 133);

-- Homocysteine
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('HCY', 'Homocysteine', '同型半胱氨酸', '13965-7', 'blood_chemistry', 'μmol/L', 1, 140);

-- Cardiac Markers
INSERT INTO lab_test_item (code, name, name_zh, loinc_code, category, unit, decimal_places, sort_order) VALUES
('CARDIAC_TROPONIN', 'High-Sensitivity Troponin I', '高敏肌钙蛋白I', '42757-5', 'blood_chemistry', 'ng/L', 1, 150),
('CARDIAC_BNP', 'B-Type Natriuretic Peptide', 'B型利钠肽', '30934-3', 'blood_chemistry', 'pg/mL', 1, 151);

-- --------------------------------------------------------------------------
-- 6.18 Lab Panel-Item assignments
-- --------------------------------------------------------------------------

-- CSF Biomarkers Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_CSF'
  AND i.code IN ('CSF_AB42', 'CSF_AB40', 'CSF_PTAU181', 'CSF_TTAU', 'CSF_NFL', 'CSF_AB42_AB40');

-- Blood Glucose Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_GLUCOSE'
  AND i.code IN ('GLU_FASTING', 'GLU_HBA1C');

-- Lipid Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_LIPID'
  AND i.code IN ('LIPID_TC', 'LIPID_HDL', 'LIPID_LDL', 'LIPID_TG');

-- CBC Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_CBC'
  AND i.code IN ('CBC_WBC', 'CBC_RBC', 'CBC_HGB', 'CBC_HCT', 'CBC_PLT', 'CBC_MCV');

-- Liver Function Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_LIVER'
  AND i.code IN ('LIVER_ALT', 'LIVER_AST', 'LIVER_ALP', 'LIVER_TBIL', 'LIVER_ALB');

-- Kidney Function Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_KIDNEY'
  AND i.code IN ('KIDNEY_CREAT', 'KIDNEY_BUN', 'KIDNEY_EGFR', 'KIDNEY_UA');

-- ApoE Genotype Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_APOE'
  AND i.code IN ('APOE_GENOTYPE');

-- Thyroid Function Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_THYROID'
  AND i.code IN ('THYROID_TSH', 'THYROID_FT3', 'THYROID_FT4');

-- Vitamin B12/Folate Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_VITAMIN'
  AND i.code IN ('VIT_B12', 'VIT_FOLATE');

-- Inflammatory Markers Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_INFLAMM'
  AND i.code IN ('INFLAMM_CRP', 'INFLAMM_ESR');

-- Coagulation Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_COAG'
  AND i.code IN ('COAG_PT', 'COAG_APTT', 'COAG_INR', 'COAG_FIB');

-- Electrolyte Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_ELECTROLYTE'
  AND i.code IN ('ELEC_NA', 'ELEC_K', 'ELEC_CL', 'ELEC_CA', 'ELEC_MG', 'ELEC_P');

-- Iron Studies Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_IRON'
  AND i.code IN ('IRON_SI', 'IRON_FERRITIN', 'IRON_TRF', 'IRON_TIBC');

-- Urinalysis Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_URINALYSIS'
  AND i.code IN ('UA_PH', 'UA_SG', 'UA_PROTEIN', 'UA_GLUCOSE');

-- Homocysteine Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_HOMOCYSTEINE'
  AND i.code IN ('HCY');

-- Cardiac Markers Panel
INSERT INTO lab_panel_item (panel_id, item_id, sort_order, is_required)
SELECT p.id, i.id, i.sort_order, 1
FROM lab_test_panel p, lab_test_item i
WHERE p.code = 'PANEL_CARDIAC'
  AND i.code IN ('CARDIAC_TROPONIN', 'CARDIAC_BNP');

-- --------------------------------------------------------------------------
-- 6.19 ICD-10 Diagnosis Reference Data
--     Common psychiatric and neurological codes
-- --------------------------------------------------------------------------
INSERT INTO diagnosis (subject_id, diagnosis_type_code_id, icd10_code, icd10_name, diagnosis_name_cn, diagnosis_date, diagnosis_source, is_primary, confirmation_status)
-- We skip row inserts here since subject_id requires actual subjects.
-- The ICD-10 reference is embedded in the table structure and comments.
-- Use this space for future data migration scripts.
SELECT 1, 1, 'REF', 'REFERENCE', 'REFERENCE', '2026-01-01', 'reference', 1, 'confirmed'
WHERE FALSE;

SET FOREIGN_KEY_CHECKS = 1;
