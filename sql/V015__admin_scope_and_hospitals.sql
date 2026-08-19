CREATE TABLE IF NOT EXISTS institution_alias (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    institution_id BIGINT UNSIGNED NOT NULL,
    alias VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_institution_alias UNIQUE (alias),
    CONSTRAINT fk_alias_institution FOREIGN KEY (institution_id) REFERENCES institution(id)
);

CREATE TABLE IF NOT EXISTS project_institution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    institution_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_institution UNIQUE (project_id, institution_id),
    CONSTRAINT fk_pi_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_pi_institution FOREIGN KEY (institution_id) REFERENCES institution(id)
);

ALTER TABLE user_role
    ADD COLUMN institution_id BIGINT UNSIGNED NULL AFTER project_id;

INSERT INTO institution (name, short_name, code, institution_type, status)
VALUES
('安徽省立医院', '省立医院', 'AHSLYY', '医院', 'active'),
('安徽省第二人民医院', '省二院', 'AHSDERMYY', '医院', 'active'),
('安徽医科大学第一附属医院', '安医大一附院', 'AYDYFY', '医院', 'active'),
('安徽省精神卫生中心', '省精神卫生中心', 'AHSJSWSZX', '医学机构', 'active'),
('滁州市第二人民医院', '滁州二院', 'CZSDERMYY', '医院', 'active')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), short_name = VALUES(short_name),
    institution_type = VALUES(institution_type), status = 'active';

INSERT IGNORE INTO institution_alias (institution_id, alias)
SELECT id, '中国科学技术大学附属第一医院' FROM institution WHERE code = 'AHSLYY';
INSERT IGNORE INTO institution_alias (institution_id, alias)
SELECT id, '中科大附属第一医院' FROM institution WHERE code = 'AHSLYY';
