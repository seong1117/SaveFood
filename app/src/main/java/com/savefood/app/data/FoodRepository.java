package com.savefood.app.data;

import com.savefood.app.model.FoodItem;
import com.savefood.app.model.Leftover;
import com.savefood.app.model.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 임시(더미) 데이터 저장소. 앱을 종료하면 데이터가 초기화됩니다.
 *
 * TODO(팀원): 이 클래스의 메서드 시그니처는 유지한 채 내부를 Firestore 로 교체하세요.
 *  - users/{uid}/foods, users/{uid}/leftovers  컬렉션 구조 권장
 *  - 화면은 이 클래스만 바라보므로 UI 코드는 수정하지 않아도 됩니다.
 */
public class FoodRepository {

    private static FoodRepository instance;

    public static synchronized FoodRepository get() {
        if (instance == null) instance = new FoodRepository();
        return instance;
    }

    private final List<FoodItem> foods = new ArrayList<>();
    private final List<Leftover> leftovers = new ArrayList<>();
    private final List<Recipe> recipes = new ArrayList<>();

    private FoodRepository() {
        seed();
    }

    // ---------- 식품 ----------
    public List<FoodItem> getFoods() {
        List<FoodItem> copy = new ArrayList<>(foods);
        Collections.sort(copy, (a, b) -> Long.compare(a.getDDay(), b.getDDay()));
        return copy;
    }

    public List<FoodItem> getUrgentFoods() {
        List<FoodItem> result = new ArrayList<>();
        for (FoodItem f : getFoods()) if (f.isUrgent()) result.add(f);
        return result;
    }

    public FoodItem getFood(String id) {
        for (FoodItem f : foods) if (f.id.equals(id)) return f;
        return null;
    }

    public void addFood(FoodItem item) {
        foods.add(item);
    }

    public void removeFood(String id) {
        for (int i = 0; i < foods.size(); i++) {
            if (foods.get(i).id.equals(id)) { foods.remove(i); return; }
        }
    }

    // ---------- 남은 요리 ----------
    public List<Leftover> getLeftovers() {
        List<Leftover> copy = new ArrayList<>(leftovers);
        Collections.sort(copy, (a, b) -> Long.compare(a.getDDay(), b.getDDay()));
        return copy;
    }

    public Leftover getLeftover(String id) {
        for (Leftover l : leftovers) if (l.id.equals(id)) return l;
        return null;
    }

    public void addLeftover(Leftover item) {
        leftovers.add(item);
    }

    public void removeLeftover(String id) {
        for (int i = 0; i < leftovers.size(); i++) {
            if (leftovers.get(i).id.equals(id)) { leftovers.remove(i); return; }
        }
    }

    // ---------- 레시피 ----------
    public List<Recipe> getRecipes() {
        return new ArrayList<>(recipes);
    }

    public Recipe getRecipe(String id) {
        for (Recipe r : recipes) if (r.id.equals(id)) return r;
        return null;
    }

    // ---------- 더미 데이터 ----------
    private void seed() {
        LocalDate today = LocalDate.now();

        foods.add(new FoodItem(uid(), "우유 (1L)", "🥛", "냉장실 · 문 선반", 1, today.minusDays(3), today.plusDays(1)));
        foods.add(new FoodItem(uid(), "연어 필렛 (200g)", "🐟", "냉장실 · 신선 보관칸", 1, today.minusDays(1), today.plusDays(1)));
        foods.add(new FoodItem(uid(), "그릭 요거트 (450g)", "🥣", "냉장실 · 위 선반", 1, today.minusDays(4), today.plusDays(3)));
        foods.add(new FoodItem(uid(), "아보카도 (2개)", "🥑", "냉장실 · 채소칸", 2, today.minusDays(2), today.plusDays(5)));
        foods.add(new FoodItem(uid(), "냉동 블루베리 (500g)", "🫐", "냉동실 · 위 서랍", 1, today.minusDays(10), today.plusDays(45)));

        leftovers.add(new Leftover(uid(), "토스카나식 마늘 치킨", "🍗", 2, today, today.plusDays(3)));
        leftovers.add(new Leftover(uid(), "구운 브로콜리", "🥦", 1, today.minusDays(1), today.plusDays(2)));

        recipes.add(recipe("r1", "연어 우유 크림 파스타", "🍝", 25, 2, "보통",
                Arrays.asList("연어 필렛", "우유"),
                new Recipe.Ingredient[]{ing("연어 필렛 200g", true), ing("우유 200ml", true), ing("파스타면 100g", true),
                        ing("마늘 3쪽", true), ing("파르메산 치즈 20g", false)},
                new Recipe.Step[]{step("면 삶기", 10, "끓는 물에 소금을 넣고 파스타면을 8분간 삶아 건져요."),
                        step("연어 굽기", 7, "연어를 한입 크기로 썰어 마늘과 함께 노릇하게 구워요."),
                        step("크림 소스 만들기", 8, "우유를 붓고 졸인 뒤 면을 넣어 버무려요.")}));

        recipes.add(recipe("r2", "연어 아보카도 볼", "🥗", 15, 1, "쉬움",
                Arrays.asList("연어 필렛"),
                new Recipe.Ingredient[]{ing("연어 필렛 150g", true), ing("아보카도 1개", true), ing("밥 1공기", true),
                        ing("간장 소스 2큰술", true), ing("김가루", false)},
                new Recipe.Step[]{step("재료 준비", 5, "연어와 아보카도를 한입 크기로 썰어요."),
                        step("올리기", 5, "밥 위에 연어와 아보카도를 올리고 소스를 뿌려요.")}));

        recipes.add(recipe("r3", "블루베리 요거트 스무디", "🥤", 5, 1, "쉬움",
                Arrays.asList("우유"),
                new Recipe.Ingredient[]{ing("냉동 블루베리 100g", true), ing("그릭 요거트 100g", true), ing("우유 150ml", true),
                        ing("얼음 4개", true), ing("꿀 1큰술", false)},
                new Recipe.Step[]{step("갈기", 3, "모든 재료를 믹서에 넣고 곱게 갈아요.")}));

        recipes.add(recipe("r4", "토스카나식 마늘 치킨", "🍗", 25, 2, "보통",
                Arrays.asList("우유"),
                new Recipe.Ingredient[]{ing("닭가슴살 2개", true), ing("마늘 5쪽", true), ing("크림 100ml", true),
                        ing("시금치 50g", true), ing("건토마토 30g", true), ing("파르메산 10g", false)},
                new Recipe.Step[]{step("재료 손질하기", 5, "닭가슴살에 소금·후추를 뿌려요. 마늘은 편 썰고, 채소는 씻어 준비해요."),
                        step("닭가슴살 굽기", 10, "팬에 올리브유 1큰술을 두르고 닭가슴살 양면을 노릇하게 구워 덜어요."),
                        step("크림 소스에 끓이기", 10, "마늘·건토마토를 볶고 크림을 넣어요. 닭·시금치를 넣어 닭 중심 75℃에서 1분.")}));
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }

    private static Recipe.Ingredient ing(String name, boolean have) {
        return new Recipe.Ingredient(name, have);
    }

    private static Recipe.Step step(String title, int minutes, String desc) {
        return new Recipe.Step(title, minutes, desc);
    }

    private static Recipe recipe(String id, String name, String emoji, int minutes, int servings, String difficulty,
                                 List<String> used, Recipe.Ingredient[] ingredients, Recipe.Step[] steps) {
        Recipe r = new Recipe();
        r.id = id;
        r.name = name;
        r.emoji = emoji;
        r.minutes = minutes;
        r.servings = servings;
        r.difficulty = difficulty;
        r.usedFoods = new ArrayList<>(used);
        r.ingredients = new ArrayList<>(Arrays.asList(ingredients));
        r.steps = new ArrayList<>(Arrays.asList(steps));
        return r;
    }
}
