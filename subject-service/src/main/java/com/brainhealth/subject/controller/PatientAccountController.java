package com.brainhealth.subject.controller;

import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.security.DataScopeGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/subjects/{subjectId}/patient-account")
public class PatientAccountController {
    private final JdbcTemplate jdbc;
    private final DataScopeGuard scopeGuard;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public PatientAccountController(JdbcTemplate jdbc, DataScopeGuard scopeGuard) {
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> status(@PathVariable Long subjectId) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI");
        scopeGuard.assertSubjectAccess(subjectId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT u.id,u.username,u.real_name AS realName,u.is_active AS active " +
            "FROM user_subject_binding b JOIN user u ON u.id=b.user_id WHERE b.subject_id=?", subjectId);
        return ApiResponse.ok(rows.isEmpty() ? Map.of("created", false)
            : new LinkedHashMap<>() {{ put("created", true); putAll(rows.get(0)); }});
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(
            @PathVariable Long subjectId, @RequestBody Map<String, Object> body) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI");
        scopeGuard.assertSubjectAccess(subjectId);
        String username = required(body, "username");
        String password = required(body, "password");
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码至少 8 位，并同时包含字母和数字");
        }
        Integer bound = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_subject_binding WHERE subject_id=?", Integer.class, subjectId);
        if (bound != null && bound > 0) throw new IllegalArgumentException("该患者已经开通过账号");
        Map<String, Object> subject = jdbc.queryForMap(
            "SELECT subject_id,enrollment_institution_id,institution_id FROM subject WHERE id=?", subjectId);
        Object institutionId = subject.get("enrollment_institution_id") != null
            ? subject.get("enrollment_institution_id") : subject.get("institution_id");
        jdbc.update("INSERT INTO user(username,password_hash,real_name,email,institution_id,is_active," +
                "must_change_password) VALUES (?,?,?,?,?,1,1)",
            username, passwordEncoder.encode(password),
            body.getOrDefault("realName", "患者" + subject.get("subject_id")),
            body.getOrDefault("email", username + "@patient.local"), institutionId);
        Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Long roleId = jdbc.queryForObject("SELECT id FROM role WHERE code='patient'", Long.class);
        jdbc.update("INSERT INTO user_role(user_id,role_id,institution_id,granted_at) VALUES (?,?,?,NOW())",
            userId, roleId, institutionId);
        jdbc.update("INSERT INTO user_subject_binding(user_id,subject_id) VALUES (?,?)", userId, subjectId);
        return ApiResponse.created(Map.of("id", userId, "username", username, "subjectId", subjectId));
    }

    @PutMapping("/status")
    public ApiResponse<Map<String, String>> updateStatus(
            @PathVariable Long subjectId, @RequestBody Map<String, Object> body) {
        scopeGuard.assertAnyRole("ADMIN", "DOCTOR", "CLINICIAN", "PI");
        scopeGuard.assertSubjectAccess(subjectId);
        boolean active = Boolean.TRUE.equals(body.get("active"));
        jdbc.update("UPDATE user u JOIN user_subject_binding b ON b.user_id=u.id " +
            "SET u.is_active=? WHERE b.subject_id=?", active, subjectId);
        return ApiResponse.ok(Map.of("message", active ? "患者账号已启用" : "患者账号已停用"));
    }

    private static String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value.toString().trim();
    }
}
