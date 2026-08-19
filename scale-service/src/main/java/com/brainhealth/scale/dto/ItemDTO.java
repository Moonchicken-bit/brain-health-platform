package com.brainhealth.scale.dto;

public class ItemDTO {
    private Long id;
    private Long instrumentId;
    private Integer itemIndex;
    private String domainName;
    private String questionText;
    private String inputType;
    private String options;
    private Integer maxScore;
    private String scoreType;

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
