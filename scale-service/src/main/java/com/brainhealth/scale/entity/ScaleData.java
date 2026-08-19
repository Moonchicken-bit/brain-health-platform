package com.brainhealth.scale.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "scale_data", uniqueConstraints = @UniqueConstraint(
    name = "uk_scaledata_assessment_item", columnNames = {"assessment_id", "item_number"}))
public class ScaleData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;
    @Column(name = "item_id")
    private Long itemId;
    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;
    @Column(name = "item_code", length = 50)
    private String itemCode;
    @Column(name = "item_text_snapshot", columnDefinition = "TEXT")
    private String itemTextSnapshot;
    @Column(name = "response_value", length = 500)
    private String responseValue;
    @Column(name = "item_score")
    private Double itemScore;

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long v) { assessmentId = v; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long v) { instrumentId = v; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long v) { itemId = v; }
    public Integer getItemNumber() { return itemNumber; }
    public void setItemNumber(Integer v) { itemNumber = v; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String v) { itemCode = v; }
    public String getItemTextSnapshot() { return itemTextSnapshot; }
    public void setItemTextSnapshot(String v) { itemTextSnapshot = v; }
    public String getResponseValue() { return responseValue; }
    public void setResponseValue(String v) { responseValue = v; }
    public Double getItemScore() { return itemScore; }
    public void setItemScore(Double v) { itemScore = v; }
}
