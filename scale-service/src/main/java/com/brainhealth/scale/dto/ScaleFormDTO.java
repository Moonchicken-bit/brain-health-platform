package com.brainhealth.scale.dto;

import java.util.List;

public class ScaleFormDTO {
    private String code;
    private String name;
    private Double maxScore;
    private Double cutoff;
    private int itemCount;
    private List<FormItemDTO> items;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getMaxScore() { return maxScore; }
    public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }
    public Double getCutoff() { return cutoff; }
    public void setCutoff(Double cutoff) { this.cutoff = cutoff; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public List<FormItemDTO> getItems() { return items; }
    public void setItems(List<FormItemDTO> items) { this.items = items; }
}
