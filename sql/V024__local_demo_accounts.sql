-- Local demonstration accounts. These credentials are for trial environments only.
UPDATE `user`
SET password_hash = '$2a$12$g7V3QlsiYNpd5nRNJnPhIui4zXo7A7oQX4mYRgK0wYAmgZ/vKXJj2',
    is_active = 1,
    is_locked = 0,
    otp_secret = NULL,
    otp_recovery_codes = NULL,
    must_change_password = 0,
    password_changed_at = NOW()
WHERE username = 'admin';

INSERT INTO `user`
    (username,password_hash,email,real_name,department,title,is_active,is_locked,must_change_password,created_at,updated_at)
SELECT
    'doctor_demo',
    '$2a$12$hBUWOJ4EgT7fmdgij80yvu1UYE4dY2k62J1Z19tMB3bkMRUAUWvhi',
    'doctor_demo@brainhealth.local','Demo Doctor','Clinical Research Center','Doctor',
    1,0,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username='doctor_demo');

UPDATE `user`
SET password_hash = '$2a$12$hBUWOJ4EgT7fmdgij80yvu1UYE4dY2k62J1Z19tMB3bkMRUAUWvhi',
    is_active=1,is_locked=0,otp_secret=NULL,otp_recovery_codes=NULL,must_change_password=0
WHERE username='doctor_demo';

UPDATE `user`
SET real_name='Demo Doctor',department='Clinical Research Center',title='Doctor',institution_id=1
WHERE username='doctor_demo';

INSERT INTO `user`
    (username,password_hash,email,real_name,department,title,is_active,is_locked,must_change_password,created_at,updated_at)
SELECT
    'trial_demo',
    '$2a$12$LMV5Dcpi8WgSdpvRG.6K6OGCEfUWCEUmtVwX3zuzL7GRixuYiiLg2',
    'trial_demo@brainhealth.local','Trial User','Project Demo','Read-only User',
    1,0,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username='trial_demo');

UPDATE `user`
SET password_hash = '$2a$12$LMV5Dcpi8WgSdpvRG.6K6OGCEfUWCEUmtVwX3zuzL7GRixuYiiLg2',
    is_active=1,is_locked=0,otp_secret=NULL,otp_recovery_codes=NULL,must_change_password=0
WHERE username='trial_demo';

UPDATE `user`
SET real_name='Trial User',department='Project Demo',title='Read-only User'
WHERE username='trial_demo';

INSERT INTO user_role (user_id,role_id,granted_at,created_at,updated_at)
SELECT u.id,r.id,NOW(),NOW(),NOW()
FROM `user` u JOIN role r ON r.code='admin'
WHERE u.username='admin'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id
  );

INSERT INTO user_role (user_id,role_id,granted_at,created_at,updated_at)
SELECT u.id,r.id,NOW(),NOW(),NOW()
FROM `user` u JOIN role r ON r.code='clinician'
WHERE u.username='doctor_demo'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id
  );

UPDATE user_role ur
JOIN `user` u ON u.id=ur.user_id
JOIN role r ON r.id=ur.role_id
SET ur.project_id=(SELECT MIN(id) FROM project),
    ur.institution_id=(SELECT MIN(id) FROM institution)
WHERE u.username='doctor_demo' AND r.code='clinician';

INSERT INTO user_role (user_id,role_id,granted_at,created_at,updated_at)
SELECT u.id,r.id,NOW(),NOW(),NOW()
FROM `user` u JOIN role r ON r.code='viewer'
WHERE u.username='trial_demo'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id
  );
