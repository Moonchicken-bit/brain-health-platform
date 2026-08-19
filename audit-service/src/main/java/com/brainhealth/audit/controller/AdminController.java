package com.brainhealth.audit.controller;
import com.brainhealth.audit.entity.AuditLog;
import org.springframework.http.ResponseEntity;
import com.brainhealth.audit.repository.AuditLogRepository;
import com.brainhealth.common.constant.Constants;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final AuditLogRepository auditRepo;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminController(JdbcTemplate jdbc, AuditLogRepository auditRepo, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.auditRepo = auditRepo;
        this.redis = redis;
    }

    @GetMapping("/visit-templates")
    public ApiResponse<List<Map<String, Object>>> listVisitTemplates() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.id,t.code,t.name,t.description,t.project_id AS projectId,t.status,
                   v.id AS versionId,v.version_no AS versionNo,v.visit_code AS visitCode,
                   v.visit_name AS visitName,v.allow_unified_upload AS allowUnifiedUpload,
                   v.required_modules AS requiredModules,v.patient_deadline_days AS patientDeadlineDays,
                   v.status AS versionStatus,v.published_at AS publishedAt
            FROM visit_template t
            LEFT JOIN visit_template_version v ON v.id=COALESCE(t.active_version_id,
                (SELECT MAX(v2.id) FROM visit_template_version v2 WHERE v2.template_id=t.id))
            ORDER BY t.id DESC
            """);
        for (Map<String, Object> row : rows) {
            Object versionId = row.get("versionId");
            row.put("scaleCodes", versionId == null ? List.of() : jdbc.queryForList(
                "SELECT scale_code FROM visit_template_scale WHERE template_version_id=? " +
                    "ORDER BY sort_order,id", String.class, versionId));
            row.put("requiredModules", parseJsonArray(row.get("requiredModules")));
        }
        return ApiResponse.ok(rows);
    }

    @PostMapping("/visit-templates")
    @Transactional
    public ApiResponse<Map<String, Object>> createVisitTemplate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        String code = String.valueOf(required(body, "code")).trim().toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO visit_template(code,name,description,project_id,status,created_by) " +
                "VALUES (?,?,?,?, 'ACTIVE',?)",
            code, required(body, "name"), body.get("description"), body.get("projectId"), userId);
        Long templateId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Long versionId = createTemplateVersion(templateId, 1, body);
        return ApiResponse.created(Map.of("id", templateId, "versionId", versionId));
    }

    @PutMapping("/visit-templates/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateVisitTemplate(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE visit_template SET name=COALESCE(?,name),description=?," +
                "project_id=? WHERE id=?",
            body.get("name"), body.get("description"), body.get("projectId"), id);
        Integer nextVersion = jdbc.queryForObject(
            "SELECT COALESCE(MAX(version_no),0)+1 FROM visit_template_version WHERE template_id=?",
            Integer.class, id);
        Long versionId = createTemplateVersion(id, nextVersion == null ? 1 : nextVersion, body);
        return ApiResponse.ok(Map.of("id", id, "versionId", versionId));
    }

    @PutMapping("/visit-templates/{id}/publish")
    @Transactional
    public ApiResponse<Map<String, String>> publishVisitTemplate(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long userId) {
        Long versionId = nullableLong(body.get("versionId"));
        if (versionId == null) {
            versionId = jdbc.queryForObject(
                "SELECT MAX(id) FROM visit_template_version WHERE template_id=?", Long.class, id);
        }
        Integer belongs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM visit_template_version WHERE id=? AND template_id=?",
            Integer.class, versionId, id);
        if (belongs == null || belongs == 0) throw new IllegalArgumentException("模板版本不存在");
        jdbc.update("UPDATE visit_template_version SET status='RETIRED' " +
            "WHERE template_id=? AND status='PUBLISHED'", id);
        jdbc.update("UPDATE visit_template_version SET status='PUBLISHED',published_by=?," +
            "published_at=NOW() WHERE id=?", userId, versionId);
        jdbc.update("UPDATE visit_template SET active_version_id=?,status='ACTIVE' WHERE id=?",
            versionId, id);
        return ApiResponse.ok(Map.of("message", "模板版本已发布"));
    }

    @PutMapping("/visit-templates/{id}/status")
    public ApiResponse<Map<String, String>> updateVisitTemplateStatus(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = Boolean.FALSE.equals(body.get("isActive")) ? "DISABLED" : "ACTIVE";
        jdbc.update("UPDATE visit_template SET status=? WHERE id=?", status, id);
        return ApiResponse.ok(Map.of("message", "模板状态已更新"));
    }

    private Long createTemplateVersion(Long templateId, int versionNo, Map<String, Object> body) {
        String visitCode = String.valueOf(required(body, "visitCode")).trim();
        String visitName = String.valueOf(body.getOrDefault("visitName", visitCode)).trim();
        String requiredModules = writeJson(body.getOrDefault("requiredModules", List.of()));
        boolean allowUpload = !Boolean.FALSE.equals(body.get("allowUnifiedUpload"));
        jdbc.update("INSERT INTO visit_template_version(template_id,version_no,visit_code,visit_name," +
                "allow_unified_upload,required_modules,patient_deadline_days,status) " +
                "VALUES (?,?,?,?,?,?,?,'DRAFT')",
            templateId, versionNo, visitCode, visitName, allowUpload, requiredModules,
            body.get("patientDeadlineDays"));
        Long versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Object rawScales = body.get("scaleCodes");
        if (rawScales instanceof Collection<?> scales) {
            int sort = 0;
            for (Object scale : scales) {
                String code = String.valueOf(scale).trim();
                if (!code.isBlank()) jdbc.update(
                    "INSERT INTO visit_template_scale(template_version_id,scale_code,sort_order) VALUES (?,?,?)",
                    versionId, code, sort++);
            }
        }
        return versionId;
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("配置内容格式错误", ex); }
    }

    private List<String> parseJsonArray(Object value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value.toString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @GetMapping("/projects")
    public ApiResponse<PageResult<Map<String, Object>>> listProjects(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        String where = " WHERE (? IS NULL OR name LIKE CONCAT('%',?,'%') OR code LIKE CONCAT('%',?,'%')) AND (? IS NULL OR status=?)";
        String normalizedStatus = hasText(status) ? status.toLowerCase(Locale.ROOT) : null;
        List<Map<String, Object>> recs = jdbc.queryForList(
            "SELECT id,name,code AS alias,principal_investigator AS pi,'' AS piContact," +
            "lead_institution_id AS institutionId,start_date AS startDate,end_date AS endDate," +
            "target_enrollment AS recruitmentTarget,UPPER(status) AS status,description,created_at AS createdAt," +
            "updated_at AS updatedAt FROM project" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            keyword, keyword, keyword, normalizedStatus, normalizedStatus, size, (page-1)*size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM project" + where, Long.class,
            keyword, keyword, keyword, normalizedStatus, normalizedStatus);
        return ApiResponse.ok(PageResult.of(page, size, total != null ? total : 0, recs));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<Map<String, Object>> getProject(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForMap("SELECT * FROM project WHERE id = ?", id));
    }

    @PostMapping("/projects")
    public ApiResponse<Map<String, String>> createProject(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", body.get("alias")));
        jdbc.update("INSERT INTO project (name,short_name,code,description,project_type,status,start_date,end_date,lead_institution_id,principal_investigator,ethics_approval_number,target_enrollment) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            required(body, "name"), body.getOrDefault("shortName", body.get("alias")), requireValue(code, "alias"),
            body.get("description"), body.getOrDefault("projectType", "observational"),
            String.valueOf(body.getOrDefault("status", "ACTIVE")).toLowerCase(Locale.ROOT),
            body.get("startDate"), body.get("endDate"),
            body.getOrDefault("leadInstitutionId", body.get("institutionId")),
            body.getOrDefault("principalInvestigator", body.get("pi")), body.get("ethicsApprovalNumber"),
            body.getOrDefault("targetEnrollment", body.get("recruitmentTarget")));
        replaceProjectInstitutions(body, jdbc.queryForObject("SELECT id FROM project WHERE code=?", Long.class, code));
        return ApiResponse.created(Map.of("message", "Project created"));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<Map<String, String>> updateProject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", body.get("alias")));
        jdbc.update("UPDATE project SET name=?,short_name=?,code=?,description=?,project_type=?,status=?,start_date=?,end_date=?,lead_institution_id=?,principal_investigator=?,ethics_approval_number=?,target_enrollment=? WHERE id=?",
            required(body,"name"), body.getOrDefault("shortName",body.get("alias")),requireValue(code,"alias"),body.get("description"),
            body.getOrDefault("projectType","observational"),
            String.valueOf(body.getOrDefault("status","ACTIVE")).toLowerCase(Locale.ROOT),
            body.get("startDate"),body.get("endDate"),body.getOrDefault("leadInstitutionId",body.get("institutionId")),
            body.getOrDefault("principalInvestigator",body.get("pi")),body.get("ethicsApprovalNumber"),
            body.getOrDefault("targetEnrollment",body.get("recruitmentTarget")),id);
        replaceProjectInstitutions(body, id);
        return ApiResponse.ok(Map.of("message", "Project updated"));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        jdbc.update("UPDATE project SET status='suspended' WHERE id=?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/institutions")
    public ApiResponse<PageResult<Map<String, Object>>> listInstitutions(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean isActive) {
        String activeStatus = isActive == null ? null : (isActive ? "active" : "inactive");
        String where = " WHERE (? IS NULL OR name LIKE CONCAT('%',?,'%') OR short_name LIKE CONCAT('%',?,'%') OR code LIKE CONCAT('%',?,'%')) AND (? IS NULL OR address LIKE CONCAT('%',?,'%')) AND (? IS NULL OR status=?)";
        List<Map<String, Object>> recs = jdbc.queryForList(
            "SELECT id,name,short_name AS alias,address AS city,description AS contact,phone AS contactPhone," +
            "address,status='active' AS isActive,created_at AS createdAt,updated_at AS updatedAt " +
            "FROM institution" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            keyword,keyword,keyword,keyword, city,city, activeStatus,activeStatus,size,(page-1)*size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM institution" + where, Long.class,
            keyword,keyword,keyword,keyword,city,city,activeStatus,activeStatus);
        return ApiResponse.ok(PageResult.of(page, size, total != null ? total : 0, recs));
    }

    @GetMapping("/institutions/{id}")
    public ApiResponse<Map<String, Object>> getInstitution(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForMap("SELECT * FROM institution WHERE id = ?", id));
    }

    @PostMapping("/institutions")
    public ApiResponse<Map<String, String>> createInstitution(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code",
            "INST" + System.currentTimeMillis()));
        jdbc.update("INSERT INTO institution (name,short_name,code,institution_type,address,phone,email,website,description,status,parent_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            required(body,"name"), body.getOrDefault("shortName",body.get("alias")), code,
            body.getOrDefault("institutionType","医院"),
            body.getOrDefault("address",body.get("city")),body.getOrDefault("phone",body.get("contactPhone")),
            body.get("email"),body.get("website"),body.getOrDefault("description",body.get("contact")),
            Boolean.FALSE.equals(body.get("isActive")) ? "inactive" : "active", body.get("parentId"));
        replaceAliases(body, jdbc.queryForObject("SELECT id FROM institution WHERE code=?", Long.class, code));
        return ApiResponse.created(Map.of("message", "Institution created"));
    }

    @PutMapping("/institutions/{id}")
    public ApiResponse<Map<String, String>> updateInstitution(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String code = body.containsKey("code") ? String.valueOf(body.get("code"))
            : jdbc.queryForObject("SELECT code FROM institution WHERE id=?", String.class, id);
        jdbc.update("UPDATE institution SET name=?,short_name=?,code=?,institution_type=?,address=?,phone=?,email=?,website=?,description=?,status=?,parent_id=? WHERE id=?",
            required(body,"name"),body.getOrDefault("shortName",body.get("alias")),code,
            body.getOrDefault("institutionType","医院"),body.getOrDefault("address",body.get("city")),
            body.getOrDefault("phone",body.get("contactPhone")),body.get("email"),body.get("website"),
            body.getOrDefault("description",body.get("contact")),
            Boolean.FALSE.equals(body.get("isActive")) ? "inactive" : "active",body.get("parentId"),id);
        replaceAliases(body, id);
        return ApiResponse.ok(Map.of("message", "Institution updated"));
    }

    @DeleteMapping("/institutions/{id}")
    public ApiResponse<Void> deleteInstitution(@PathVariable Long id) {
        jdbc.update("UPDATE institution SET status='inactive' WHERE id=?", id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/institutions/{id}/status")
    public ApiResponse<Map<String, String>> toggleInstitution(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE institution SET status=? WHERE id=?",
            Boolean.FALSE.equals(body.get("isActive")) ? "inactive" : "active", id);
        return ApiResponse.ok(Map.of("message", "Status updated"));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        String where = " WHERE (? IS NULL OR username LIKE CONCAT('%',?,'%') OR real_name LIKE CONCAT('%',?,'%') OR email LIKE CONCAT('%',?,'%'))";
        List<Map<String, Object>> recs = jdbc.queryForList(
            "SELECT u.id,u.username,u.real_name as realName,u.email,u.phone,u.institution_id as institutionId," +
                "u.department,u.title,u.is_active as isActive," +
                "(SELECT GROUP_CONCAT(DISTINCT r.code ORDER BY r.code) FROM user_role ur " +
                " JOIN role r ON r.id=ur.role_id WHERE ur.user_id=u.id) AS roleCodes," +
                "(SELECT GROUP_CONCAT(DISTINCT ur.project_id ORDER BY ur.project_id) FROM user_role ur " +
                " WHERE ur.user_id=u.id AND ur.project_id IS NOT NULL) AS projectIdsCsv FROM user u"
                + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            keyword, keyword, keyword, keyword, size, (page-1)*size);
        recs.forEach(row -> {
            row.put("roles", splitStrings(row.remove("roleCodes")));
            row.put("projectIds", splitLongs(row.remove("projectIdsCsv")));
        });
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM user" + where, Long.class,
            keyword, keyword, keyword, keyword);
        return ApiResponse.ok(PageResult.of(page, size, total != null ? total : 0, recs));
    }

    @PostMapping("/users")
    @Transactional
    public ApiResponse<Map<String, String>> createUser(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(required(body, "username")).trim();
        String password = String.valueOf(required(body, "password"));
        validatePassword(password);
        jdbc.update("INSERT INTO user (username, password_hash, real_name, email, phone, institution_id, is_active, must_change_password) VALUES (?,?,?,?,?,?,?,?)",
            username, passwordEncoder.encode(password), body.getOrDefault("realName", ""),
            body.getOrDefault("email", ""), body.getOrDefault("phone", ""),
            body.getOrDefault("institutionId", 1), true, true);
        Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        replaceUserRoles(userId, body);
        return ApiResponse.created(Map.of("message", "User created"));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ApiResponse<Map<String, String>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE user SET real_name=?, email=?, phone=?, department=?, title=?," +
                "institution_id=COALESCE(?,institution_id) WHERE id=?",
            body.getOrDefault("realName", ""), body.getOrDefault("email", ""),
            body.getOrDefault("phone", ""), body.getOrDefault("department", ""),
            body.getOrDefault("title", ""), body.get("institutionId"), id);
        if (body.containsKey("roles")) replaceUserRoles(id, body);
        return ApiResponse.ok(Map.of("message", "User updated"));
    }

    @PutMapping("/users/{id}/status")
    @Transactional
    public ApiResponse<Map<String, String>> updateUserStatus(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long currentUserId) {
        Object raw = body.get("isActive");
        if (!(raw instanceof Boolean active)) throw new IllegalArgumentException("isActive must be boolean");
        if (!active && Objects.equals(id, currentUserId)) throw new IllegalArgumentException("不能停用当前登录账号");
        Integer adminMemberships = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='admin'",
            Integer.class, id);
        if (!active && adminMemberships != null && adminMemberships > 0) {
            Integer activeAdmins = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT u.id) FROM user u JOIN user_role ur ON ur.user_id=u.id " +
                    "JOIN role r ON r.id=ur.role_id WHERE u.is_active=1 AND r.code='admin'", Integer.class);
            if (activeAdmins != null && activeAdmins <= 1) {
                throw new IllegalArgumentException("不能停用最后一个系统管理员");
            }
        }
        if (jdbc.update("UPDATE user SET is_active=? WHERE id=?", active, id) == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!active) {
            redis.delete(Constants.REDIS_REFRESH_TOKEN + id);
            redis.delete(Constants.REDIS_USER_PERMISSIONS + id);
            redis.opsForValue().set("user:disabled:" + id, "1");
        } else {
            redis.delete("user:disabled:" + id);
        }
        return ApiResponse.ok(Map.of("message", active ? "User enabled" : "User disabled"));
    }

    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("newPassword");
        validatePassword(password);
        int changed = jdbc.update(
            "UPDATE user SET password_hash=?, must_change_password=1, password_changed_at=NOW() WHERE id=?",
            passwordEncoder.encode(password), id);
        if (changed == 0) throw new IllegalArgumentException("用户不存在");
        return ApiResponse.ok(Map.of("message", "Password reset"));
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<Map<String, Object>>> getUserRoles(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForList("SELECT r.id,r.name,r.code FROM role r JOIN user_role ur ON r.id=ur.role_id WHERE ur.user_id=?", id));
    }

    @PostMapping("/users/{userId}/roles")
    public ApiResponse<Map<String, String>> assignRole(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        jdbc.update("INSERT INTO user_role (user_id,role_id,project_id,institution_id,granted_at) VALUES (?,?,?,?,NOW())",
            userId, required(body,"roleId"), body.get("projectId"), body.get("institutionId"));
        return ApiResponse.ok(Map.of("message", "Role assigned"));
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ApiResponse<Void> removeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", userId, roleId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> listRoles() {
        List<Map<String, Object>> roles = jdbc.queryForList("SELECT * FROM role ORDER BY sort_order,id");
        roles.forEach(role -> role.put("permissions", jdbc.queryForList(
            "SELECT permission_id FROM role_permission WHERE role_id=? ORDER BY permission_id",
            Long.class, role.get("id"))));
        return ApiResponse.ok(roles);
    }

    @PostMapping("/roles")
    @Transactional
    public ApiResponse<Map<String, String>> createRole(@RequestBody Map<String, Object> body) {
        String code = requiredText(body, "code");
        jdbc.update("INSERT INTO role (name,code,description,is_system,is_active) VALUES (?,?,?,0,1)",
            requiredText(body, "name"), code, body.getOrDefault("description", ""));
        Long roleId = jdbc.queryForObject("SELECT id FROM role WHERE code=?", Long.class, code);
        replaceRolePermissions(roleId, permissionIds(body));
        return ApiResponse.created(Map.of("message", "Role created"));
    }

    @PutMapping("/roles/{id}")
    @Transactional
    public ApiResponse<Map<String, String>> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (jdbc.update("UPDATE role SET name=?,description=? WHERE id=?",
            requiredText(body, "name"), body.getOrDefault("description", ""), id) == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        replaceRolePermissions(id, permissionIds(body));
        return ApiResponse.ok(Map.of("message", "Role updated"));
    }

    @DeleteMapping("/roles/{id}")
    @Transactional
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        Boolean system = jdbc.queryForObject("SELECT is_system FROM role WHERE id=?", Boolean.class, id);
        if (Boolean.TRUE.equals(system)) throw new IllegalArgumentException("系统内置角色不能删除");
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE role_id=?", Integer.class, id);
        if (users != null && users > 0) throw new IllegalArgumentException("角色仍被用户使用，不能删除");
        jdbc.update("DELETE FROM role WHERE id=?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<List<Map<String, Object>>> getRolePermissions(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForList("SELECT p.id,p.code,p.name,p.resource,p.action FROM permission p JOIN role_permission rp ON p.id=rp.permission_id WHERE rp.role_id=?", id));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> listPermissions() {
        return ApiResponse.ok(jdbc.queryForList("SELECT * FROM permission"));
    }

    @GetMapping("/dynamic-fields")
    public ApiResponse<List<Map<String, Object>>> listDynamicFields(
            @RequestParam(defaultValue = "GENETICS") String module) {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT fd.id,fd.form_id AS formId,f.code AS formCode,f.name AS formName," +
            "fd.field_code AS fieldCode,fd.label,fd.description,fd.field_type AS fieldType," +
            "fd.unit,fd.default_value AS defaultValue,fd.options_json AS optionsJson," +
            "fd.validation_json AS validationJson,fd.required_flag AS requiredFlag," +
            "fd.sort_order AS sortOrder,fd.status,f.version " +
            "FROM field_definition fd JOIN form_definition f ON f.id=fd.form_id " +
            "WHERE f.module=? ORDER BY fd.sort_order,fd.id", module.toUpperCase(Locale.ROOT)));
    }

    @PostMapping("/dynamic-fields")
    @Transactional
    public ApiResponse<Map<String, Object>> createDynamicField(@RequestBody Map<String, Object> body) {
        String module = String.valueOf(body.getOrDefault("module", "GENETICS")).toUpperCase(Locale.ROOT);
        String formCode = String.valueOf(body.getOrDefault("formCode", module + "_SAMPLE"));
        List<Long> formIds = jdbc.queryForList(
            "SELECT id FROM form_definition WHERE code=? ORDER BY version DESC LIMIT 1", Long.class, formCode);
        Long formId;
        if (formIds.isEmpty()) {
            jdbc.update(
                "INSERT INTO form_definition(code,name,module,version,status) VALUES (?,?,?,1,'PUBLISHED')",
                formCode, String.valueOf(body.getOrDefault("formName", defaultFormName(module))),
                module);
            formId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            formId = formIds.get(0);
        }
        jdbc.update(
            "INSERT INTO field_definition(form_id,field_code,label,description,field_type,unit,default_value," +
            "options_json,validation_json,required_flag,sort_order,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            formId, requiredText(body, "fieldCode"), requiredText(body, "label"),
            body.get("description"), requiredText(body, "fieldType").toUpperCase(Locale.ROOT),
            body.get("unit"), body.get("defaultValue"), jsonValue(body.get("options")),
            jsonValue(body.get("validation")), Boolean.TRUE.equals(body.get("required")),
            body.getOrDefault("sortOrder", 0), body.getOrDefault("status", "DRAFT"));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.created(Map.of("id", id, "module", module));
    }

    private String defaultFormName(String module) {
        return switch (module) {
            case "IMAGING" -> "影像检查扩展信息";
            case "LAB" -> "实验室检查扩展信息";
            case "GENETICS" -> "遗传样本扩展信息";
            default -> module + " 扩展信息";
        };
    }

    @PutMapping("/dynamic-fields/{id}")
    public ApiResponse<Map<String, String>> updateDynamicField(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbc.update(
            "UPDATE field_definition SET label=?,description=?,field_type=?,unit=?,default_value=?," +
            "options_json=?,validation_json=?,required_flag=?,sort_order=? WHERE id=?",
            requiredText(body, "label"), body.get("description"),
            requiredText(body, "fieldType").toUpperCase(Locale.ROOT), body.get("unit"),
            body.get("defaultValue"), jsonValue(body.get("options")), jsonValue(body.get("validation")),
            Boolean.TRUE.equals(body.get("required")), body.getOrDefault("sortOrder", 0), id);
        return ApiResponse.ok(Map.of("message", "Field updated"));
    }

    @PutMapping("/dynamic-fields/{id}/status")
    public ApiResponse<Map<String, String>> updateDynamicFieldStatus(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = requiredText(body, "status").toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("字段状态无效");
        }
        jdbc.update("UPDATE field_definition SET status=? WHERE id=?", status, id);
        return ApiResponse.ok(Map.of("message", "Field status updated"));
    }

    @GetMapping("/visit-fields")
    public ApiResponse<List<Map<String, Object>>> visitFields(
            @RequestParam(required = false) String visitCode) {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT id,visit_code AS visitCode,section_code AS sectionCode,field_code AS fieldCode," +
            "label,field_type AS fieldType,unit,options_json AS optionsJson,required_flag AS requiredFlag," +
            "status,version,sort_order AS sortOrder FROM visit_form_custom_field " +
            "WHERE (? IS NULL OR visit_code=?) ORDER BY visit_code,sort_order,id", visitCode, visitCode));
    }

    @PostMapping("/visit-fields")
    public ApiResponse<Map<String, Object>> createVisitField(@RequestBody Map<String, Object> body) {
        jdbc.update("INSERT INTO visit_form_custom_field(visit_code,section_code,field_code,label," +
                "field_type,unit,options_json,required_flag,status,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
            requiredText(body,"visitCode"), body.getOrDefault("sectionCode","CUSTOM"),
            requiredText(body,"fieldCode"),requiredText(body,"label"),
            requiredText(body,"fieldType").toUpperCase(Locale.ROOT),body.get("unit"),
            jsonValue(body.get("options")),Boolean.TRUE.equals(body.get("required")),
            body.getOrDefault("status","DRAFT"),body.getOrDefault("sortOrder",0));
        return ApiResponse.created(Map.of("id", Objects.requireNonNull(
            jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class))));
    }

    @PutMapping("/visit-fields/{id}")
    public ApiResponse<Map<String, String>> updateVisitField(
            @PathVariable Long id,@RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE visit_form_custom_field SET label=?,field_type=?,unit=?,options_json=?," +
                "required_flag=?,sort_order=? WHERE id=?",requiredText(body,"label"),
            requiredText(body,"fieldType").toUpperCase(Locale.ROOT),body.get("unit"),
            jsonValue(body.get("options")),Boolean.TRUE.equals(body.get("required")),
            body.getOrDefault("sortOrder",0),id);
        return ApiResponse.ok(Map.of("message","Visit field updated"));
    }

    @PutMapping("/visit-fields/{id}/status")
    public ApiResponse<Map<String, String>> updateVisitFieldStatus(
            @PathVariable Long id,@RequestBody Map<String, Object> body) {
        String status=requiredText(body,"status").toUpperCase(Locale.ROOT);
        if(!Set.of("DRAFT","PUBLISHED","DISABLED").contains(status)) {
            throw new IllegalArgumentException("字段状态无效");
        }
        jdbc.update("UPDATE visit_form_custom_field SET status=? WHERE id=?",status,id);
        return ApiResponse.ok(Map.of("message","Visit field status updated"));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<Map<String, Object>>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        AuditQuery query = auditQuery(userId, action, resourceType, status, dateFrom, dateTo);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        List<Object> pageArgs = new ArrayList<>(query.args());
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(
            auditSelect() + query.where() + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log a" + query.where(),
            Long.class, query.args().toArray());
        return ApiResponse.ok(PageResult.of(safePage, safeSize, total == null ? 0 : total, records));
    }

    @GetMapping("/audit-logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        AuditQuery query = auditQuery(userId, action, resourceType, status, dateFrom, dateTo);
        List<Object> args = new ArrayList<>(query.args());
        args.add(10000);
        List<Map<String, Object>> rows = jdbc.queryForList(
            auditSelect() + query.where() + " ORDER BY a.created_at DESC LIMIT ?", args.toArray());
        StringBuilder sb = new StringBuilder();
        sb.append("id,username,action,resource_type,detail,created_at\n");
        for (Map<String, Object> row : rows) {
            sb.append(escapeCsv(row.get("id"))).append(",");
            sb.append(escapeCsv(row.get("username"))).append(",");
            sb.append(escapeCsv(row.get("action"))).append(",");
            sb.append(escapeCsv(row.get("resourceType"))).append(",");
            sb.append(escapeCsv(row.get("detail"))).append(",");
            sb.append(escapeCsv(row.get("createdAt"))).append("\n");
        }
        byte[] content = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=audit_logs.csv")
            .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .body(content);
    }

    private static String auditSelect() {
        return "SELECT a.id,a.user_id as userId,COALESCE(u.username,a.username) username," +
            "a.operation_type AS action,a.target_type as resourceType,a.target_id as resourceId," +
            "a.operation_detail AS detail,a.operation_ip as ipAddress,a.operation_result AS status," +
            "a.created_at as createdAt " +
            "FROM audit_log a LEFT JOIN user u ON a.user_id=u.id";
    }

    private AuditQuery auditQuery(Long userId, String action, String resourceType, String status,
                                  String dateFrom, String dateTo) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (userId != null) { clauses.add("a.user_id=?"); args.add(userId); }
        if (hasText(action)) { clauses.add("a.operation_type=?"); args.add(action.trim()); }
        if (hasText(resourceType)) { clauses.add("a.target_type=?"); args.add(resourceType.trim()); }
        if (hasText(status)) { clauses.add("a.operation_result=?"); args.add(status.trim()); }
        if (hasText(dateFrom)) { clauses.add("a.created_at>=?"); args.add(LocalDate.parse(dateFrom).atStartOfDay()); }
        if (hasText(dateTo)) { clauses.add("a.created_at<?"); args.add(LocalDate.parse(dateTo).plusDays(1).atStartOfDay()); }
        return new AuditQuery(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), args);
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        if (!permissionIds.isEmpty()) {
            Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM permission WHERE id IN (" +
                    String.join(",", Collections.nCopies(permissionIds.size(), "?")) + ")",
                Integer.class, permissionIds.toArray());
            if (found == null || found != permissionIds.size()) throw new IllegalArgumentException("包含不存在的权限");
        }
        jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
        permissionIds.forEach(permissionId -> jdbc.update(
            "INSERT INTO role_permission(role_id,permission_id) VALUES (?,?)", roleId, permissionId));
    }

    private static List<Long> permissionIds(Map<String, Object> body) {
        Object raw = body.get("permissions");
        if (!(raw instanceof Collection<?> values)) return List.of();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Object value : values) {
            try { ids.add(Long.valueOf(value.toString())); }
            catch (Exception e) { throw new IllegalArgumentException("权限 ID 格式错误"); }
        }
        return List.copyOf(ids);
    }

    private static String requiredText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.toString().trim();
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String jsonValue(Object value) {
        if (value == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("字段配置不是有效 JSON");
        }
    }
    private record AuditQuery(String where, List<Object> args) {}

    private Object required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("缺少必填字段：" + key);
        }
        return value;
    }
    private static String requireValue(String value, String key) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("缺少必填字段：" + key);
        }
        return value.trim();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须为 8–128 位，并同时包含字母和数字");
        }
    }

    private void replaceAliases(Map<String, Object> body, Long institutionId) {
        if (!body.containsKey("aliases")) return;
        jdbc.update("DELETE FROM institution_alias WHERE institution_id=?", institutionId);
        Object raw = body.get("aliases");
        Collection<?> aliases = raw instanceof Collection<?> values
            ? values : Arrays.asList(String.valueOf(raw == null ? "" : raw).split("[,，\\n]"));
        for (Object alias : aliases) {
            String value = String.valueOf(alias).trim();
            if (!value.isEmpty()) {
                jdbc.update("INSERT INTO institution_alias (institution_id,alias) VALUES (?,?)",
                    institutionId, value);
            }
        }
    }

    private void replaceProjectInstitutions(Map<String, Object> body, Long projectId) {
        if (!body.containsKey("institutionIds")) return;
        jdbc.update("DELETE FROM project_institution WHERE project_id=?", projectId);
        Object raw = body.get("institutionIds");
        if (!(raw instanceof Collection<?> ids)) return;
        for (Object id : ids) {
            if (id != null) {
                jdbc.update("INSERT INTO project_institution (project_id,institution_id) VALUES (?,?)",
                    projectId, Long.valueOf(id.toString()));
            }
        }
    }

    private void replaceUserRoles(Long userId, Map<String, Object> body) {
        Object rawRoles = body.get("roles");
        if (!(rawRoles instanceof Collection<?> roles) || roles.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个角色");
        }
        Long institutionId = nullableLong(body.get("institutionId"));
        List<Long> projectIds = body.get("projectIds") instanceof Collection<?> values
            ? values.stream().map(this::nullableLong).filter(Objects::nonNull).distinct().toList()
            : List.of();
        jdbc.update("DELETE FROM user_role WHERE user_id=?", userId);
        for (Object roleValue : roles) {
            String roleCode = String.valueOf(roleValue);
            List<Long> roleIds = jdbc.queryForList("SELECT id FROM role WHERE code=?", Long.class, roleCode);
            if (roleIds.isEmpty()) throw new IllegalArgumentException("角色不存在：" + roleCode);
            Long roleId = roleIds.get(0);
            if (projectIds.isEmpty() || Set.of("admin", "patient").contains(roleCode.toLowerCase(Locale.ROOT))) {
                jdbc.update("INSERT INTO user_role(user_id,role_id,project_id,institution_id,granted_at) " +
                    "VALUES (?,?,?,?,NOW())", userId, roleId, null, institutionId);
            } else {
                for (Long projectId : projectIds) {
                    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=?",
                        Integer.class, projectId);
                    if (exists == null || exists == 0) {
                        throw new IllegalArgumentException("项目不存在：" + projectId);
                    }
                    jdbc.update("INSERT INTO user_role(user_id,role_id,project_id,institution_id,granted_at) " +
                        "VALUES (?,?,?,?,NOW())", userId, roleId, projectId, institutionId);
                }
            }
        }
        redis.delete(Constants.REDIS_USER_PERMISSIONS + userId);
    }

    private static List<String> splitStrings(Object value) {
        if (value == null || value.toString().isBlank()) return List.of();
        return Arrays.stream(value.toString().split(",")).filter(item -> !item.isBlank()).toList();
    }

    private static List<Long> splitLongs(Object value) {
        if (value == null || value.toString().isBlank()) return List.of();
        return Arrays.stream(value.toString().split(",")).filter(item -> !item.isBlank())
            .map(Long::valueOf).toList();
    }

    private Long nullableLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Long.valueOf(value.toString());
    }

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String s = val.toString().replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    // ---- Code Table Lookups ----

    @GetMapping("/marital-statuses")
    public ApiResponse<List<Map<String, Object>>> listMaritalStatuses() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, code, name FROM marital_status_code ORDER BY sort_order");
        if (rows.isEmpty()) rows = jdbc.queryForList("SELECT id, code, name FROM code_marital_status ORDER BY sort_order");
        return ApiResponse.ok(rows);
    }

    @GetMapping("/blood-types")
    public ApiResponse<List<Map<String, Object>>> listBloodTypes() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, code, name FROM blood_type_code ORDER BY sort_order");
        if (rows.isEmpty()) rows = jdbc.queryForList("SELECT id, code, name FROM code_blood_type ORDER BY sort_order");
        return ApiResponse.ok(rows);
    }
}
