package com.brainhealth.subject.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "subject_project_note")
public class SubjectProjectNote extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    public Long getId() { return id; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
