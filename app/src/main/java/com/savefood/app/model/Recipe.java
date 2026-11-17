package com.savefood.app.model;

import java.util.ArrayList;
import java.util.List;

public class Recipe {
    public String id;
    public String name;
    public String emoji;
    public int minutes;
    public int servings;
    public String difficulty;                       // 쉬움 / 보통 / 어려움
    public List<String> usedFoods = new ArrayList<>(); // 사용되는 임박 재료 이름(칩)
    public int score;                               // Recommend score / 추천 점수
    public boolean custom;                          // 사용자가 직접 추가한 레시피
    public List<Ingredient> ingredients = new ArrayList<>();
    public List<Step> steps = new ArrayList<>();

    public static class Ingredient {
        public String foodName;   // 푸드 이름 매칭
        public String amount;
        public boolean have;      // 있다 | 없다

        public Ingredient(String foodName, String amount) {
            this.foodName = foodName;
            this.amount = amount;
        }
        public String display() { return (foodName + " " + amount).trim(); }
    }

    public static class Step {
        public String title;
        public int minutes;
        public String description;

        public Step(String title, int minutes, String description) {
            this.title = title;
            this.minutes = minutes;
            this.description = description;
        }
    }

    public int haveCount() {
        int c = 0;
        for (Ingredient i : ingredients) if (i.have) c++;
        return c;
    }

    public int totalCount() {
        return ingredients.size();
    }
}
