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

UPDATE institution
SET code = 'AHSLYY', short_name = '省立医院', institution_type = '医院', status = 'active'
WHERE name = '安徽省立医院';

UPDATE institution
SET institution_type = '医院', status = 'active'
WHERE name IN ('安徽医科大学第一附属医院', '皖南医学院弋矶山医院', '蚌埠医学院第一附属医院');
UPDATE institution
SET institution_type = '医学机构', status = 'active'
WHERE name = '安徽省精神卫生中心';

INSERT INTO institution (name, short_name, code, institution_type, status)
SELECT '安徽省第二人民医院', '省二院', 'AHSEY', '医院', 'active'
WHERE NOT EXISTS (SELECT 1 FROM institution WHERE name = '安徽省第二人民医院');

INSERT INTO institution (name, short_name, code, institution_type, status)
SELECT '滁州市第二人民医院', '滁州二院', 'CZSEY', '医院', 'active'
WHERE NOT EXISTS (SELECT 1 FROM institution WHERE name = '滁州市第二人民医院');

INSERT IGNORE INTO institution_alias (institution_id, alias)
SELECT id, '中国科学技术大学附属第一医院'
FROM institution WHERE name = '安徽省立医院' LIMIT 1;
INSERT IGNORE INTO institution_alias (institution_id, alias)
SELECT id, '中科大附属第一医院'
FROM institution WHERE name = '安徽省立医院' LIMIT 1;

-- Retain the old duplicate row for auditability, but prevent it being assigned.
UPDATE institution
SET status = 'inactive',
    description = '历史重复机构，已并入安徽省立医院（中国科学技术大学附属第一医院）'
WHERE name = '中国科学技术大学附属第一医院';

INSERT IGNORE INTO project_institution (project_id, institution_id)
SELECT 1, id FROM institution WHERE status = 'active' AND institution_type IN ('医院', '医学机构');
