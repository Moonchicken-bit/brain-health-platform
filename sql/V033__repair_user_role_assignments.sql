UPDATE role SET name='患者', description='仅填写本人获分配的量表任务'
WHERE code='patient';

DELETE duplicate_role
FROM user_role duplicate_role
JOIN user_role retained_role
  ON retained_role.user_id=duplicate_role.user_id
 AND retained_role.role_id=duplicate_role.role_id
 AND COALESCE(retained_role.project_id,0)=COALESCE(duplicate_role.project_id,0)
 AND COALESCE(retained_role.institution_id,0)=COALESCE(duplicate_role.institution_id,0)
 AND retained_role.id < duplicate_role.id;
