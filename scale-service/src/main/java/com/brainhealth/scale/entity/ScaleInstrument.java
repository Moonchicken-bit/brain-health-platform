package com.brainhealth.scale.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scale_instrument")
public class ScaleInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_zh", length = 500)
    private String nameZh;

    @Column(length = 50)
    private String abbreviation;

    @Column(length = 50)
    private String version;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_score_min")
    private Double totalScoreMin;

    @Column(name = "total_score_max")
    private Double totalScoreMax;

    @Column(name = "cutoff_score")
    private Double cutoffScore;

    @Column(name = "administration_time_min")
    private Integer administrationTimeMin;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String v) { this.nameZh = v; }
    public String getAbbreviation() { return abbreviation; }
    public void setAbbreviation(String v) { this.abbreviation = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Double getTotalScoreMin() { return totalScoreMin; }
    public void setTotalScoreMin(Double v) { this.totalScoreMin = v; }
    public Double getTotalScoreMax() { return totalScoreMax; }
    public void setTotalScoreMax(Double v) { this.totalScoreMax = v; }
    public Double getCutoffScore() { return cutoffScore; }
    public void setCutoffScore(Double v) { this.cutoffScore = v; }
    public Integer getAdministrationTimeMin() { return administrationTimeMin; }
    public void setAdministrationTimeMin(Integer v) { this.administrationTimeMin = v; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean v) { this.isActive = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
