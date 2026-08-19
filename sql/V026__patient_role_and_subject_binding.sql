CREATE TABLE IF NOT EXISTS user_subject_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_patient_user (user_id),
    UNIQUE KEY uk_patient_subject (subject_id),
    CONSTRAINT fk_patient_binding_user FOREIGN KEY (user_id) REFERENCES `user`(id),
    CONSTRAINT fk_patient_binding_subject FOREIGN KEY (subject_id) REFERENCES subject(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO role (code, name, description, is_system, created_at, updated_at)
SELECT 'patient', '患者', '仅填写本人量表任务', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'patient');

INSERT INTO role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM role r JOIN permission p ON p.code IN ('scale:view', 'scale:create', 'scale:edit')
WHERE r.code = 'patient'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE ur FROM user_role ur
JOIN `user` u ON u.id = ur.user_id
JOIN role r ON r.id = ur.role_id
WHERE u.username = 'trial_demo' AND r.code = 'viewer';

INSERT INTO user_role (user_id, role_id, granted_at, created_at, updated_at)
SELECT u.id, r.id, NOW(), NOW(), NOW()
FROM `user` u JOIN role r ON r.code = 'patient'
WHERE u.username = 'trial_demo'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO user_subject_binding (user_id, subject_id, created_at)
SELECT u.id, s.id, NOW()
FROM `user` u JOIN subject s ON s.subject_id = 'SUB-001'
WHERE u.username = 'trial_demo'
  AND NOT EXISTS (SELECT 1 FROM user_subject_binding b WHERE b.user_id = u.id);

UPDATE `user`
SET real_name = '患者试用账号', department = NULL, title = '患者'
WHERE username = 'trial_demo';
