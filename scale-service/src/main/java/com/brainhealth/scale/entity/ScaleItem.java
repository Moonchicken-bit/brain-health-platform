package com.brainhealth.scale.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "scale_item")
public class ScaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "item_index", nullable = false)
    private Integer itemIndex;

    @Column(name = "domain_name", length = 100)
    private String domainName;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "input_type", length = 20)
    private String inputType;

    @Column(length = 500)
    private String options;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;

    @Column(name = "score_type", length = 20)
    private String scoreType;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long v) { this.instrumentId = v; }
    public Integer getItemIndex() { return itemIndex; }
    public void setItemIndex(Integer v) { this.itemIndex = v; }
    public String getDomainName() { return domainName; }
    public void setDomainName(String v) { this.domainName = v; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String v) { this.questionText = v; }
    public String getInputType() { return inputType; }
    public void setInputType(String v) { this.inputType = v; }
    public String getOptions() { return options; }
    public void setOptions(String v) { this.options = v; }
    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer v) { this.maxScore = v; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String v) { this.scoreType = v; }
}
