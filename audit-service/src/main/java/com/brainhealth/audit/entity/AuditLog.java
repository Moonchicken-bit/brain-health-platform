package com.brainhealth.audit.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100)
    private String username;
    @Column(name = "operation_type", length = 32)
    private String action;
    @Column(name = "target_type", length = 48)
    private String resourceType;
    @Column(name = "target_id")
    private Long resourceId;
    @Column(name = "operation_detail", columnDefinition = "LONGTEXT")
    private String detail;
    @Column(name = "operation_ip", length = 45)
    private String ipAddress;
    @Column(name = "operation_result", length = 16)
    private String status = "SUCCESS";
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getUsername() { return username; } public void setUsername(String v) { username = v; }
    public String getAction() { return action; } public void setAction(String v) { action = v; }
    public String getResourceType() { return resourceType; } public void setResourceType(String v) { resourceType = v; }
    public Long getResourceId() { return resourceId; } public void setResourceId(Long v) { resourceId = v; }
    public String getDetail() { return detail; } public void setDetail(String v) { detail = v; }
    public String getIpAddress() { return ipAddress; } public void setIpAddress(String v) { ipAddress = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
}
