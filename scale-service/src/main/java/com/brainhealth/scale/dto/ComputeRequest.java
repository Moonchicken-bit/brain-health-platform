package com.brainhealth.scale.dto;

import java.util.Map;

public class ComputeRequest {
    private String instrument;
    private Map<String, String> responses;

    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }
    public Map<String, String> getResponses() { return responses; }
    public void setResponses(Map<String, String> responses) { this.responses = responses; }
}
