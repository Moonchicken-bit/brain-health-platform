package com.brainhealth.scale.dto;

import java.util.List;

public class AssessmentItemRequest {
    private List<Item> items;
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> v) { items = v; }

    public static class Item {
        private Integer itemIndex;
        private String questionText;
        private Object response;
        private Double score;
        public Integer getItemIndex() { return itemIndex; }
        public void setItemIndex(Integer v) { itemIndex = v; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String v) { questionText = v; }
        public Object getResponse() { return response; }
        public void setResponse(Object v) { response = v; }
        public Double getScore() { return score; }
        public void setScore(Double v) { score = v; }
    }
}
