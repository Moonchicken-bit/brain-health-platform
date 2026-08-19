package com.brainhealth.auth.dto;

import java.io.Serializable;
import java.util.List;

public class UserProfileResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private UserInfoDTO user;
    private List<String> permissions;

    public UserProfileResponse() {}

    public UserInfoDTO getUser() {
        return user;
    }

    public void setUser(UserInfoDTO user) {
        this.user = user;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    // ---- inner class ----

    public static class UserInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String username;
        private String realName;
        private String email;
        private String phone;
        private Long institutionId;
        private Long subjectId;
        private String department;
        private List<String> roles;

        public UserInfoDTO() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public Long getInstitutionId() {
            return institutionId;
        }

        public Long getSubjectId() { return subjectId; }
        public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

        public void setInstitutionId(Long institutionId) {
            this.institutionId = institutionId;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
