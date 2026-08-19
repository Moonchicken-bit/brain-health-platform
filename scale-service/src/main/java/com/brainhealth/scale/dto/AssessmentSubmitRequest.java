package com.brainhealth.scale.dto;
import java.util.List;

public class AssessmentSubmitRequest {
    private List<ItemScore> scores;
    public List<ItemScore> getScores() { return scores; }
    public void setScores(List<ItemScore> v) { this.scores = v; }

    public static class ItemScore {
        private int itemIndex;
        private double score;
        public int getItemIndex() { return itemIndex; }
        public void setItemIndex(int v) { this.itemIndex = v; }
        public double getScore() { return score; }
        public void setScore(double v) { this.score = v; }
    }
}
