-- ============================================================================
-- Flyway Migration V002: Hospital Bridge Module
-- Description: Hospital integration — patient, hospital_encounter,
--   patient_subject_mapping, medical_record
-- Requires: V001 (user, subject, facility, department)
-- Standards: GB/T 2261.1 (sex), GB/T 3304 (ethnicity/nation),
--   GB/T 4658 (education), GB/T 2659 (nationality)
-- ============================================================================

-- Compatibility masters required by the hospital bridge. They were referenced
-- by the original migration but never created in V001.
CREATE TABLE IF NOT EXISTS `facility` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `institution_id` BIGINT UNSIGNED DEFAULT NULL,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_facility_code` (`code`),
    CONSTRAINT `fk_facility_institution`
        FOREIGN KEY (`institution_id`) REFERENCES `institution` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `department` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `facility_id` BIGINT UNSIGNED DEFAULT NULL,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_department_facility_code` (`facility_id`, `code`),
    CONSTRAINT `fk_department_facility`
        FOREIGN KEY (`facility_id`) REFERENCES `facility` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 1. patient — 医院患者信息表 (Hospital Patient Master)
-- Stores patient demographic data imported from hospital HIS/EMR systems.
-- PHI fields (name, ID card, phone, address) are stored encrypted/masked.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `patient` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT '主键ID',
    `patient_uuid`          CHAR(36)        NOT NULL
        COMMENT 'External/API-facing UUID',
    `facility_id`           BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> facility — 就诊医院/机构',
    `hospital_patient_id`   VARCHAR(64)     NOT NULL
        COMMENT '医院内部患者ID/病案号',
    `name`                  VARCHAR(128)    NOT NULL
        COMMENT '患者姓名（加密存储）',
    `name_pinyin`           VARCHAR(256)            DEFAULT NULL
        COMMENT '姓名拼音',
    `sex`                   TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '性别: 0=未知, 1=男, 2=女 (GB/T 2261.1)',
    `date_of_birth`         DATE                    DEFAULT NULL
        COMMENT '出生日期',
    `date_of_birth_precise` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '出生日期精确度: 0=精确, 1=仅年月, 2=仅年, 3=不详',
    `age`                   INT UNSIGNED            DEFAULT NULL
        COMMENT '年龄（冗余计算字段，单位：岁）',
    `nation`                VARCHAR(32)             DEFAULT NULL
        COMMENT '民族 (GB/T 3304): 01=汉族, 02=蒙古族, ...',
    `nationality`           VARCHAR(32)             DEFAULT NULL
        COMMENT '国籍 (GB/T 2659): CHN=中国, USA=美国, ...',
    `education`             VARCHAR(32)             DEFAULT NULL
        COMMENT '文化程度 (GB/T 4658): 10=研究生, 20=本科, 30=大专, 40=中专/中技, 50=高中, 60=初中, 70=小学, 80=文盲/半文盲',
    `marital_status`        VARCHAR(16)             DEFAULT NULL
        COMMENT '婚姻状况: UNKNOWN, UNMARRIED, MARRIED, DIVORCED, WIDOWED',
    `blood_type`            VARCHAR(8)              DEFAULT NULL
        COMMENT '血型: A, B, AB, O, UNKNOWN',
    `blood_rh`              VARCHAR(4)              DEFAULT NULL
        COMMENT 'Rh血型: POS, NEG, UNKNOWN',
    `id_card_type`          VARCHAR(32)             DEFAULT NULL
        COMMENT '证件类型: ID_CARD=身份证, PASSPORT=护照, MILITARY=军官证, MTP=港澳通行证, TW_PASS=台胞证, OTHER',
    `id_card_number`        VARCHAR(128)            DEFAULT NULL
        COMMENT '证件号码（加密存储）',
    `phone`                 VARCHAR(128)            DEFAULT NULL
        COMMENT '联系电话（加密存储）',
    `contact_name`          VARCHAR(128)            DEFAULT NULL
        COMMENT '联系人姓名（加密存储）',
    `contact_phone`         VARCHAR(128)            DEFAULT NULL
        COMMENT '联系人电话（加密存储）',
    `contact_relationship`  VARCHAR(32)             DEFAULT NULL
        COMMENT '联系人与患者关系: SPOUSE, PARENT, CHILD, SIBLING, OTHER',
    `address`               VARCHAR(512)            DEFAULT NULL
        COMMENT '家庭住址（加密存储）',
    `domicile_province`     VARCHAR(32)             DEFAULT NULL
        COMMENT '户籍省份代码 (GB/T 2260)',
    `domicile_city`         VARCHAR(32)             DEFAULT NULL
        COMMENT '户籍城市代码 (GB/T 2260)',
    `domicile_district`     VARCHAR(32)             DEFAULT NULL
        COMMENT '户籍区县代码 (GB/T 2260)',
    `residence_province`    VARCHAR(32)             DEFAULT NULL
        COMMENT '现住地省份代码 (GB/T 2260)',
    `residence_city`        VARCHAR(32)             DEFAULT NULL
        COMMENT '现住地城市代码 (GB/T 2260)',
    `residence_district`    VARCHAR(32)             DEFAULT NULL
        COMMENT '现住地区县代码 (GB/T 2260)',
    `occupation`            VARCHAR(64)             DEFAULT NULL
        COMMENT '职业',
    `insurance_type`        VARCHAR(32)             DEFAULT NULL
        COMMENT '医保类型: UEBMI=城镇职工, URRBMI=城乡居民, NRCMS=新农合, SELF_PAY=自费, COMMERCIAL=商业保险, OTHER',
    `insurance_number`      VARCHAR(128)            DEFAULT NULL
        COMMENT '医保卡号（加密存储）',
    `height_cm`             DECIMAL(5,1)            DEFAULT NULL
        COMMENT '身高（厘米）',
    `weight_kg`             DECIMAL(5,1)            DEFAULT NULL
        COMMENT '体重（千克）',
    `bmi`                   DECIMAL(4,1)            DEFAULT NULL
        COMMENT 'BMI（身体质量指数）',
    `source_system`         VARCHAR(64)             DEFAULT NULL
        COMMENT '数据来源系统: HIS, EMR, LIS, PACS, MANUAL',
    `source_patient_id`     VARCHAR(128)            DEFAULT NULL
        COMMENT '源系统患者ID',
    `source_last_sync_at`   DATETIME                DEFAULT NULL
        COMMENT '源系统最后同步时间',
    `is_merged`             TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT '是否已合并: 0=否, 1=是（此记录已被合并到其他患者）',
    `merged_into_patient_id` BIGINT UNSIGNED        DEFAULT NULL
        COMMENT 'FK -> patient — 合并目标患者ID（自身合并）',
    `merge_reason`          VARCHAR(256)            DEFAULT NULL
        COMMENT '合并原因',
    `data_quality_score`    TINYINT UNSIGNED        DEFAULT NULL
        COMMENT '数据质量评分（0-100）',
    `status`                VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
        COMMENT '状态: ACTIVE=活跃, INACTIVE=非活跃, MERGED=已合并, ARCHIVED=已归档',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '记录最后更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_patient_uuid`          (`patient_uuid`),
    UNIQUE KEY `uk_facility_hospital_pid` (`facility_id`, `hospital_patient_id`),
    INDEX      `idx_patient_facility`     (`facility_id`),
    INDEX      `idx_patient_name`         (`name`),
    INDEX      `idx_patient_sex`          (`sex`),
    INDEX      `idx_patient_dob`          (`date_of_birth`),
    INDEX      `idx_patient_nation`       (`nation`),
    INDEX      `idx_patient_phone`        (`phone`),
    INDEX      `idx_patient_id_card`      (`id_card_number`),
    INDEX      `idx_patient_source_system`(`source_system`),
    INDEX      `idx_patient_source_pid`   (`source_system`, `source_patient_id`),
    INDEX      `idx_patient_status`       (`status`),
    INDEX      `idx_patient_merged_into`  (`merged_into_patient_id`),
    INDEX      `idx_patient_created_at`   (`created_at`),

    CONSTRAINT `fk_patient_facility`
        FOREIGN KEY (`facility_id`) REFERENCES `facility` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_patient_merged_into`
        FOREIGN KEY (`merged_into_patient_id`) REFERENCES `patient` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='医院患者信息表 — 存储从HIS/EMR系统导入的患者人口学信息';


-- ----------------------------------------------------------------------------
-- 2. hospital_encounter — 医院就诊记录表 (Hospital Encounter / Visit)
-- Records each hospital visit: outpatient, inpatient, emergency, physical exam.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hospital_encounter` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT '主键ID',
    `encounter_uuid`        CHAR(36)        NOT NULL
        COMMENT 'External/API-facing UUID',
    `patient_id`            BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — 患者ID',
    `facility_id`           BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> facility — 就诊机构ID',
    `department_id`         BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> department — 就诊科室ID',
    `encounter_type`        VARCHAR(24)     NOT NULL
        COMMENT '就诊类型: OUTPATIENT=门诊, INPATIENT=住院, EMERGENCY=急诊, DAY_SURGERY=日间手术, PHYSICAL_EXAM=体检',
    `admission_number`      VARCHAR(64)             DEFAULT NULL
        COMMENT '住院号/就诊流水号',
    `visit_number`          INT UNSIGNED            DEFAULT NULL
        COMMENT '就诊次数（同一患者第几次就诊）',
    `admission_time`        DATETIME                DEFAULT NULL
        COMMENT '入院/就诊时间',
    `discharge_time`        DATETIME                DEFAULT NULL
        COMMENT '出院时间',
    `length_of_stay_days`   INT UNSIGNED            DEFAULT NULL
        COMMENT '住院天数',
    `admission_department_id` BIGINT UNSIGNED       DEFAULT NULL
        COMMENT 'FK -> department — 入院科室ID',
    `discharge_department_id` BIGINT UNSIGNED       DEFAULT NULL
        COMMENT 'FK -> department — 出院科室ID',
    `transfer_department_id`  BIGINT UNSIGNED       DEFAULT NULL
        COMMENT 'FK -> department — 转科至',
    `attending_doctor_id`   BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 主治医生ID',
    `admitting_doctor_id`   BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 入院医生ID',
    `discharge_doctor_id`   BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 出院医生ID',
    `ward_name`             VARCHAR(128)            DEFAULT NULL
        COMMENT '病区名称',
    `ward_code`             VARCHAR(32)             DEFAULT NULL
        COMMENT '病区代码',
    `bed_number`            VARCHAR(32)             DEFAULT NULL
        COMMENT '床位号',
    `admission_diagnosis`   TEXT                    DEFAULT NULL
        COMMENT '入院诊断（ICD-10编码, 逗号分隔）',
    `admission_diagnosis_desc` VARCHAR(512)         DEFAULT NULL
        COMMENT '入院诊断描述',
    `discharge_diagnosis`   TEXT                    DEFAULT NULL
        COMMENT '出院主要诊断（ICD-10编码）',
    `discharge_diagnosis_desc` VARCHAR(512)         DEFAULT NULL
        COMMENT '出院诊断描述',
    `comorbidity_diagnosis` TEXT                    DEFAULT NULL
        COMMENT '合并症诊断（ICD-10编码, 逗号分隔）',
    `complication_diagnosis` TEXT                   DEFAULT NULL
        COMMENT '并发症诊断（ICD-10编码）',
    `admission_condition`   VARCHAR(32)             DEFAULT NULL
        COMMENT '入院情况: STABLE=一般, ACUTE=急, CRITICAL=危重, EMERGENCY=急诊',
    `discharge_condition`   VARCHAR(32)             DEFAULT NULL
        COMMENT '出院情况: CURED=治愈, IMPROVED=好转, UNCHANGED=未愈, DEAD=死亡, TRANSFERRED=转院, AUTO_DISCHARGE=自动出院',
    `discharge_disposition` VARCHAR(64)             DEFAULT NULL
        COMMENT '出院去向: HOME, TRANSFERRED_TO_HOSPITAL, REHAB_FACILITY, NURSING_HOME, DECEASED, OTHER',
    `surgery_count`         INT UNSIGNED    NOT NULL DEFAULT 0
        COMMENT '住院期间手术次数',
    `total_cost`            DECIMAL(12,2)           DEFAULT NULL
        COMMENT '总费用（元）',
    `insurance_pay_amount`  DECIMAL(12,2)           DEFAULT NULL
        COMMENT '医保支付金额（元）',
    `self_pay_amount`       DECIMAL(12,2)           DEFAULT NULL
        COMMENT '自费金额（元）',
    `source_system`         VARCHAR(64)             DEFAULT NULL
        COMMENT '数据来源系统: HIS, EMR, MANUAL',
    `source_encounter_id`   VARCHAR(128)            DEFAULT NULL
        COMMENT '源系统就诊记录ID',
    `source_last_sync_at`   DATETIME                DEFAULT NULL
        COMMENT '源系统最后同步时间',
    `note`                  TEXT                    DEFAULT NULL
        COMMENT '备注',
    `status`                VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
        COMMENT '状态: ACTIVE=活跃, COMPLETED=已完结, CANCELLED=已取消, ARCHIVED=已归档',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '记录最后更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_encounter_uuid`             (`encounter_uuid`),
    UNIQUE KEY `uk_source_encounter`           (`source_system`, `source_encounter_id`),
    INDEX      `idx_encounter_patient`         (`patient_id`),
    INDEX      `idx_encounter_facility`        (`facility_id`),
    INDEX      `idx_encounter_department`      (`department_id`),
    INDEX      `idx_encounter_type`            (`encounter_type`),
    INDEX      `idx_encounter_admission_time`  (`admission_time`),
    INDEX      `idx_encounter_discharge_time`  (`discharge_time`),
    INDEX      `idx_encounter_admission_number`(`admission_number`),
    INDEX      `idx_encounter_attending_doctor`(`attending_doctor_id`),
    INDEX      `idx_encounter_admission_dept`  (`admission_department_id`),
    INDEX      `idx_encounter_discharge_dept`  (`discharge_department_id`),
    INDEX      `idx_encounter_discharge_cond`  (`discharge_condition`),
    INDEX      `idx_encounter_source_system`   (`source_system`),
    INDEX      `idx_encounter_status`          (`status`),
    INDEX      `idx_encounter_patient_time`    (`patient_id`, `admission_time`),
    INDEX      `idx_encounter_facility_time`   (`facility_id`, `admission_time`),

    CONSTRAINT `fk_encounter_patient`
        FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_facility`
        FOREIGN KEY (`facility_id`) REFERENCES `facility` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_department`
        FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_admission_department`
        FOREIGN KEY (`admission_department_id`) REFERENCES `department` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_discharge_department`
        FOREIGN KEY (`discharge_department_id`) REFERENCES `department` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_transfer_department`
        FOREIGN KEY (`transfer_department_id`) REFERENCES `department` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_attending_doctor`
        FOREIGN KEY (`attending_doctor_id`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_admitting_doctor`
        FOREIGN KEY (`admitting_doctor_id`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_encounter_discharge_doctor`
        FOREIGN KEY (`discharge_doctor_id`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='医院就诊记录表 — 记录患者每次门/急诊、住院、体检的就诊信息';


-- ----------------------------------------------------------------------------
-- 3. patient_subject_mapping — 患者-受试者映射表
-- Bridges hospital patients with research subjects for data integration.
-- Supports one-to-one, merged, and split mapping scenarios.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `patient_subject_mapping` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT '主键ID',
    `mapping_uuid`      CHAR(36)        NOT NULL
        COMMENT 'External/API-facing UUID',
    `patient_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — 患者ID',
    `subject_id`        BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> subject — 受试者ID',
    `mapping_type`      VARCHAR(24)     NOT NULL DEFAULT 'ONE_TO_ONE'
        COMMENT '映射类型: ONE_TO_ONE=一对一, MERGED=多患者合并到一受试者, SPLIT=一患者拆分到多受试者, CROSS_FACILITY=跨机构同一受试者',
    `mapping_status`    VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
        COMMENT '映射状态: ACTIVE=活跃, INACTIVE=已解绑, PENDING_REVIEW=待审核, REJECTED=已驳回',
    `mapped_by`         BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 映射操作人ID',
    `mapped_at`         DATETIME                DEFAULT NULL
        COMMENT '映射建立时间',
    `mapping_confidence` DECIMAL(3,2)           DEFAULT NULL
        COMMENT '映射置信度 (0.00-1.00): 1.00=完全确定, >=0.70=高置信度自动匹配',
    `mapping_method`    VARCHAR(24)             DEFAULT NULL
        COMMENT '映射方式: MANUAL=人工匹配, AUTO_NAME_DOB=姓名+出生日期自动匹配, AUTO_ID_CARD=身份证号自动匹配, AUTO_PHONE=手机号自动匹配, AUTO_PROBABILISTIC=概率匹配, BATCH_IMPORT=批量导入',
    `mapping_rules_applied` VARCHAR(512)        DEFAULT NULL
        COMMENT '应用的匹配规则描述',
    `match_fields`      JSON                    DEFAULT NULL
        COMMENT '匹配字段详情（JSON: 匹配到的字段及分值）',
    `is_primary`        TINYINT(1)      NOT NULL DEFAULT 1
        COMMENT '是否为主映射: 0=否, 1=是（一个受试者关联多个患者时标记主记录）',
    `unmapped_by`       BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 解绑操作人ID',
    `unmapped_at`       DATETIME                DEFAULT NULL
        COMMENT '解绑时间',
    `unmapped_reason`   VARCHAR(256)            DEFAULT NULL
        COMMENT '解绑原因',
    `reviewed_by`       BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 审核人ID',
    `reviewed_at`       DATETIME                DEFAULT NULL
        COMMENT '审核时间',
    `review_comment`    VARCHAR(512)            DEFAULT NULL
        COMMENT '审核意见',
    `notes`             TEXT                    DEFAULT NULL
        COMMENT '映射备注',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '记录最后更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mapping_uuid`           (`mapping_uuid`),
    UNIQUE KEY `uk_patient_subject`        (`patient_id`, `subject_id`),
    INDEX      `idx_psm_patient`           (`patient_id`),
    INDEX      `idx_psm_subject`           (`subject_id`),
    INDEX      `idx_psm_mapping_type`      (`mapping_type`),
    INDEX      `idx_psm_mapping_status`    (`mapping_status`),
    INDEX      `idx_psm_mapped_by`         (`mapped_by`),
    INDEX      `idx_psm_mapped_at`         (`mapped_at`),
    INDEX      `idx_psm_mapping_confidence`(`mapping_confidence`),
    INDEX      `idx_psm_is_primary`        (`is_primary`),
    INDEX      `idx_psm_unmapped_at`       (`unmapped_at`),
    INDEX      `idx_psm_reviewed_by`       (`reviewed_by`),
    INDEX      `idx_psm_subject_active`    (`subject_id`, `mapping_status`),

    CONSTRAINT `fk_psm_patient`
        FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_psm_subject`
        FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_psm_mapped_by`
        FOREIGN KEY (`mapped_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_psm_unmapped_by`
        FOREIGN KEY (`unmapped_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_psm_reviewed_by`
        FOREIGN KEY (`reviewed_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='患者-受试者映射表 — 桥接医院患者和研究受试者，支持多种映射场景';


-- ----------------------------------------------------------------------------
-- 4. medical_record — 电子病历记录表 (Electronic Medical Record)
-- Stores structured medical record data imported from hospital EMR system.
-- Includes admission notes, progress notes, discharge summaries, etc.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `medical_record` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT '主键ID',
    `record_uuid`           CHAR(36)        NOT NULL
        COMMENT 'External/API-facing UUID',
    `patient_id`            BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — 患者ID',
    `encounter_id`          BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> hospital_encounter — 关联就诊记录',
    `subject_id`            BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> subject — 关联受试者ID（可选，通过映射获得）',
    `record_type`           VARCHAR(32)     NOT NULL
        COMMENT '病历类型: ADMISSION_NOTE=入院记录, PROGRESS_NOTE=病程记录, DISCHARGE_SUMMARY=出院小结, OPERATION_RECORD=手术记录, CONSULTATION=会诊记录, EMERGENCY_RECORD=急诊记录, NURSING_RECORD=护理记录, TRANSFER_NOTE=转科记录, DEATH_RECORD=死亡记录, DISCHARGE_INSTRUCTION=出院医嘱, DIAGNOSIS_CERTIFICATE=诊断证明, PHYSICAL_EXAM_RECORD=体检记录',
    `record_title`          VARCHAR(256)            DEFAULT NULL
        COMMENT '病历标题',
    `record_date`           DATETIME                DEFAULT NULL
        COMMENT '病历记录日期',
    `record_department_id`  BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> department — 记录科室ID',
    `record_doctor_id`      BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 记录医生ID',
    `chief_complaint`       TEXT                    DEFAULT NULL
        COMMENT '主诉',
    `present_illness`       TEXT                    DEFAULT NULL
        COMMENT '现病史',
    `past_history`          TEXT                    DEFAULT NULL
        COMMENT '既往史',
    `personal_history`      TEXT                    DEFAULT NULL
        COMMENT '个人史',
    `family_history`        TEXT                    DEFAULT NULL
        COMMENT '家族史',
    `menstrual_history`     TEXT                    DEFAULT NULL
        COMMENT '月经婚育史（女性）',
    `physical_examination`  TEXT                    DEFAULT NULL
        COMMENT '体格检查',
    `vital_signs`           JSON                    DEFAULT NULL
        COMMENT '生命体征（JSON: temperature, pulse, respiration, blood_pressure_systolic, blood_pressure_diastolic, spo2, pain_score）',
    `auxiliary_examination` TEXT                    DEFAULT NULL
        COMMENT '辅助检查结果',
    `preliminary_diagnosis` TEXT                    DEFAULT NULL
        COMMENT '初步诊断（ICD-10编码, 逗号分隔）',
    `confirmed_diagnosis`   TEXT                    DEFAULT NULL
        COMMENT '确定诊断（ICD-10编码, 逗号分隔）',
    `differential_diagnosis` TEXT                   DEFAULT NULL
        COMMENT '鉴别诊断',
    `diagnosis_basis`       TEXT                    DEFAULT NULL
        COMMENT '诊断依据',
    `treatment_plan`        TEXT                    DEFAULT NULL
        COMMENT '诊疗计划',
    `treatment_measures`    TEXT                    DEFAULT NULL
        COMMENT '治疗措施（药物、手术、康复等）',
    `medication_orders`     JSON                    DEFAULT NULL
        COMMENT '用药医嘱（JSON数组: drug_name, dosage, frequency, route, start_date, end_date）',
    `doctor_notes`          TEXT                    DEFAULT NULL
        COMMENT '医师意见/备注',
    `record_status`         VARCHAR(24)     NOT NULL DEFAULT 'DRAFT'
        COMMENT '病历状态: DRAFT=草稿, PRELIMINARY=初步, CONFIRMED=已确认, SIGNED=已签名, ARCHIVED=已归档',
    `is_signed`             TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT '是否已签名: 0=否, 1=是',
    `signed_by`             BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 签名人ID',
    `signed_at`             DATETIME                DEFAULT NULL
        COMMENT '签名时间',
    `is_abnormal`           TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT '是否为异常/重点关注记录: 0=否, 1=是',
    `abnormal_flag_reason`  VARCHAR(256)            DEFAULT NULL
        COMMENT '异常标注原因',
    `icd_primary_code`      VARCHAR(16)             DEFAULT NULL
        COMMENT '主要诊断ICD-10编码',
    `icd_primary_desc`      VARCHAR(256)            DEFAULT NULL
        COMMENT '主要诊断ICD-10描述',
    `icd_secondary_codes`   VARCHAR(512)            DEFAULT NULL
        COMMENT '次要诊断ICD-10编码（逗号分隔）',
    `source_system`         VARCHAR(64)             DEFAULT NULL
        COMMENT '数据来源系统: EMR, HIS, MANUAL',
    `source_record_id`      VARCHAR(128)            DEFAULT NULL
        COMMENT '源系统病历ID',
    `source_last_sync_at`   DATETIME                DEFAULT NULL
        COMMENT '源系统最后同步时间',
    `is_nlp_processed`      TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT '是否已进行NLP结构化处理: 0=否, 1=是',
    `nlp_processed_at`      DATETIME                DEFAULT NULL
        COMMENT 'NLP处理时间',
    `nlp_entities`          JSON                    DEFAULT NULL
        COMMENT 'NLP提取实体（JSON: 症状、疾病、药物、手术、时间等）',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '记录最后更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_uuid`            (`record_uuid`),
    UNIQUE KEY `uk_source_record`          (`source_system`, `source_record_id`),
    INDEX      `idx_mr_patient`            (`patient_id`),
    INDEX      `idx_mr_encounter`          (`encounter_id`),
    INDEX      `idx_mr_subject`            (`subject_id`),
    INDEX      `idx_mr_record_type`        (`record_type`),
    INDEX      `idx_mr_record_date`        (`record_date`),
    INDEX      `idx_mr_record_department`  (`record_department_id`),
    INDEX      `idx_mr_record_doctor`      (`record_doctor_id`),
    INDEX      `idx_mr_record_status`      (`record_status`),
    INDEX      `idx_mr_is_signed`          (`is_signed`),
    INDEX      `idx_mr_is_abnormal`        (`is_abnormal`),
    INDEX      `idx_mr_icd_primary_code`   (`icd_primary_code`),
    INDEX      `idx_mr_source_system`      (`source_system`),
    INDEX      `idx_mr_is_nlp_processed`   (`is_nlp_processed`),
    INDEX      `idx_mr_created_at`         (`created_at`),
    INDEX      `idx_mr_patient_date`       (`patient_id`, `record_date`),
    INDEX      `idx_mr_patient_type`       (`patient_id`, `record_type`),
    INDEX      `idx_mr_encounter_type`     (`encounter_id`, `record_type`),

    CONSTRAINT `fk_mr_patient`
        FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_mr_encounter`
        FOREIGN KEY (`encounter_id`) REFERENCES `hospital_encounter` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_mr_subject`
        FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_mr_record_department`
        FOREIGN KEY (`record_department_id`) REFERENCES `department` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_mr_record_doctor`
        FOREIGN KEY (`record_doctor_id`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT `fk_mr_signed_by`
        FOREIGN KEY (`signed_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='电子病历记录表 — 存储从EMR系统导入的结构化病历文书';


-- ----------------------------------------------------------------------------
-- 5. patient_merge_log — 患者合并日志表 (Patient Merge Audit Log)
-- Tracks patient record deduplication / merge operations.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `patient_merge_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT '主键ID',
    `source_patient_id` BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — 被合并的源患者ID',
    `target_patient_id` BIGINT UNSIGNED NOT NULL
        COMMENT 'FK -> patient — 合并目标患者ID',
    `merge_type`      VARCHAR(24)     NOT NULL DEFAULT 'MANUAL'
        COMMENT '合并类型: MANUAL=人工合并, AUTO_EXACT=精确自动合并, AUTO_PROBABILISTIC=概率自动合并',
    `merge_confidence` DECIMAL(3,2)           DEFAULT NULL
        COMMENT '合并置信度 (0.00-1.00)',
    `merged_by`       BIGINT UNSIGNED         DEFAULT NULL
        COMMENT 'FK -> user — 合并操作人ID',
    `merge_reason`    VARCHAR(256)            DEFAULT NULL
        COMMENT '合并原因说明',
    `records_transferred` INT UNSIGNED        DEFAULT 0
        COMMENT '转移的记录数量',
    `merge_detail`    JSON                    DEFAULT NULL
        COMMENT '合并详情（JSON: 匹配字段、得分、逻辑等）',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '记录最后更新时间',

    PRIMARY KEY (`id`),
    INDEX `idx_pml_source_patient` (`source_patient_id`),
    INDEX `idx_pml_target_patient` (`target_patient_id`),
    INDEX `idx_pml_merge_type`     (`merge_type`),
    INDEX `idx_pml_merged_by`      (`merged_by`),
    INDEX `idx_pml_created_at`     (`created_at`),

    CONSTRAINT `fk_pml_source_patient`
        FOREIGN KEY (`source_patient_id`) REFERENCES `patient` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_pml_target_patient`
        FOREIGN KEY (`target_patient_id`) REFERENCES `patient` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT `fk_pml_merged_by`
        FOREIGN KEY (`merged_by`) REFERENCES `user` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='患者合并日志表 — 记录患者去重/合并操作的历史';
