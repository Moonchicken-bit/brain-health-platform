package com.brainhealth.scale.dto;

import java.util.List;

public class FormItemDTO {
    private String code;
    private String name;
    private String type;
    private List<OptionDTO> options;
    private Boolean required;
    private String unit;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<OptionDTO> getOptions() { return options; }
    public void setOptions(List<OptionDTO> options) { this.options = options; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
