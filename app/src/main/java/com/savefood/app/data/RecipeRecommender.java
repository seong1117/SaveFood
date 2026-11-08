package com.savefood.app.data;

import com.savefood.app.model.FoodItem;
import com.savefood.app.model.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecipeRecommender {

    // Sort options / 정렬 기준
    public enum Sort { URGENT_FIRST, QUICK, LEAST_MISSING }

    // Clean name for matching: remove (..) and spaces / 매칭용 이름 정리: 괄호·공백 제거
    static String normalize(String text) {
        return text.replaceAll("\\(.*?\\)", "").replaceAll("\\s+", "").trim();
    }

    // Calculate score for each recipe / 레시피별 점수 계산
    public static void evaluate(Recipe recipe, List<FoodItem> foods) {
        recipe.usedFoods.clear();
        recipe.score = 0;
        int missingCount = 0;
        for (Recipe.Ingredient ingredient : recipe.ingredients) {
            FoodItem matchedFood = null;
            String ingredientName = normalize(ingredient.foodName);
            for (FoodItem food : foods) {
                String foodName = normalize(food.name);
                if (foodName.contains(ingredientName) || ingredientName.contains(foodName)) {
                    matchedFood = food;
                    break;
                }
            }
            ingredient.have = matchedFood != null;
            if (matchedFood == null) {
                missingCount++;
                continue;
            }
            long daysLeft = matchedFood.getDDay();
            if (daysLeft <= 3) {                    // Expiring soon / 소비기한 임박 재료
                recipe.usedFoods.add(ingredient.foodName);
                recipe.score += Math.max(1, 4 - daysLeft) * 10;
            }
        }
        recipe.score -= missingCount * 5;   // Minus for missing items / 부족 재료 감점
    }

    // Score and sort recipes / 점수 계산 후 정렬
    public static List<Recipe> recommend(List<Recipe> recipes, List<FoodItem> foods, Sort sort) {
        for (Recipe recipe : recipes) evaluate(recipe, foods);
        List<Recipe> result = new ArrayList<>(recipes);
        switch (sort) {
            case QUICK:
                result.removeIf(recipe -> recipe.minutes > 30);   // 30 min or less / 30분 이하만
                result.sort((first, second) -> Integer.compare(second.score, first.score));
                break;
            case LEAST_MISSING:
                result.sort(Comparator
                        .comparingInt((Recipe recipe) -> recipe.totalCount() - recipe.haveCount())
                        .thenComparing(recipe -> -recipe.score));
                break;
            default:
                result.sort((first, second) -> Integer.compare(second.score, first.score));
        }
        return result;
    }
}
