package com.brainhealth.scale.dto;
import java.util.List;

public class InstrumentDetailDTO {
    private InstrumentDTO instrument;
    private List<ItemDTO> items;
    public InstrumentDTO getInstrument() { return instrument; }
    public void setInstrument(InstrumentDTO v) { this.instrument = v; }
    public List<ItemDTO> getItems() { return items; }
    public void setItems(List<ItemDTO> v) { this.items = v; }
}
