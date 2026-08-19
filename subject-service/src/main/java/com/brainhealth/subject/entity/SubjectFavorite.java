package com.brainhealth.subject.entity;

import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "subject_favorite", uniqueConstraints =
    @UniqueConstraint(name = "uk_subject_favorite_user_subject", columnNames = {"user_id", "subject_id"}))
public class SubjectFavorite extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
}
