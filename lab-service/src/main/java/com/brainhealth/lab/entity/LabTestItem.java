package com.brainhealth.lab.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "lab_test_item")
public class LabTestItem extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "item_name", nullable = false, length = 200)
    private String name;
    @Column(length = 50)
    private String category;
    @Column(length = 50)
    private String unit;
    @Column(name = "reference_low")
    private Double referenceMin;
    @Column(name = "reference_high")
    private Double referenceMax;
    @Column(columnDefinition = "TEXT")
    private String description;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { category = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { unit = v; }
    public Double getReferenceMin() { return referenceMin; }
    public void setReferenceMin(Double v) { referenceMin = v; }
    public Double getReferenceMax() { return referenceMax; }
    public void setReferenceMax(Double v) { referenceMax = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
}
