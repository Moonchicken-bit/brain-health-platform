package com.brainhealth.scale.dto;

public class InstrumentDTO {
    private Long id;
    private String code;
    private String name;
    private String nameZh;
    private String abbreviation;
    private String version;
    private String category;
    private String description;
    private Double totalScoreMin;
    private Double totalScoreMax;
    private Double cutoffScore;
    private Integer administrationTimeMin;

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
}
