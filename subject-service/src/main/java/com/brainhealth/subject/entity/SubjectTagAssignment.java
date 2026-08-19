package com.brainhealth.subject.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "subject_tag_assignment", uniqueConstraints =
    @UniqueConstraint(name = "uk_subject_tag_assignment", columnNames = {"subject_id", "tag_id"}))
public class SubjectTagAssignment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "tag_id", nullable = false)
    private Long tagId;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    public Long getId() { return id; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
