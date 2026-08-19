package com.brainhealth.subject.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "subject_business_tag", uniqueConstraints =
    @UniqueConstraint(name = "uk_subject_tag_project_name", columnNames = {"project_id", "name"}))
public class SubjectBusinessTag extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(length = 20)
    private String color;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
