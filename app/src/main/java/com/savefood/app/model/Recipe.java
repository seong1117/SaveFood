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
    public List<Ingredient> ingredients = new ArrayList<>();
    public List<Step> steps = new ArrayList<>();

    public static class Ingredient {
        public String name;    // 예: "닭가슴살 2개"
        public boolean have;   // 보유 여부

        public Ingredient(String name, boolean have) {
            this.name = name;
            this.have = have;
        }
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
