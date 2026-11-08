package com.savefood.app.data;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.savefood.app.model.FoodItem;
import com.savefood.app.model.Leftover;
import com.savefood.app.model.Recipe;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 데이터 저장소.
 *  - 식품(foods): Firestore users/{uid}/foods 와 실시간 동기화
 *  - 남은 요리 / 레시피: 아직 더미 데이터 (담당 팀원이 Firestore 로 교체 예정)
 *
 * 화면은 기존과 똑같이 getFoods() 등을 호출하면 됩니다.
 * 단, Firestore 데이터는 비동기로 도착하므로 화면에서
 * addOnChangeListener() 로 변경 알림을 받아 목록을 다시 그려야 합니다.
 */
public class FoodRepository {

    private static final String TAG = "FoodRepository";

    // Firestore 필드명 (팀 Firestore 모델링과 맞출 것)
    private static final String F_NAME = "name";
    private static final String F_EMOJI = "emoji";
    private static final String F_LOCATION = "location";
    private static final String F_QUANTITY = "quantity";
    private static final String F_STATUS = "status";
    private static final String F_REGISTERED = "registeredDate";
    private static final String F_EXPIRY = "expiryDate";
    private static final String F_UPDATED_AT = "updatedAt";

    private static FoodRepository instance;

    public static synchronized FoodRepository get() {
        if (instance == null) instance = new FoodRepository();
        return instance;
    }

    private final List<FoodItem> foods = new ArrayList<>();
    private final List<Leftover> leftovers = new ArrayList<>();
    private final List<Recipe> recipes = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration foodsReg;
    private String currentUid;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private FoodRepository() {
        seed();
        // 로그인하면 동기화 시작, 로그아웃하면 중지 (등록 즉시 현재 상태로 한 번 호출됨)
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) startFoodsSync(user.getUid());
            else stopFoodsSync();
        });
    }

    // ---------- 변경 알림 ----------
    /** 식품 목록이 바뀌면 호출됨 (메인 스레드). 화면 onStart 에서 등록, onStop 에서 해제 */
    public void addOnChangeListener(Runnable listener) {
        if (!changeListeners.contains(listener)) changeListeners.add(listener);
    }

    public void removeOnChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable r : new ArrayList<>(changeListeners)) r.run();
    }

    // ---------- Firestore 동기화 ----------
    private CollectionReference foodsRef() {
        return db.collection("users").document(currentUid).collection("foods");
    }

    private void startFoodsSync(String uid) {
        if (uid.equals(currentUid)) return; // 이미 같은 사용자로 동기화 중
        stopFoodsSync();
        currentUid = uid;

        foodsReg = foodsRef()
                .orderBy(F_EXPIRY, Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e(TAG, "식품 목록 수신 실패", e); return; }
                    if (snap == null) return;

                    foods.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        FoodItem f = fromDoc(d);
                        if (f != null) foods.add(f);
                    }
                    notifyChanged();
                });
    }

    private void stopFoodsSync() {
        if (foodsReg != null) { foodsReg.remove(); foodsReg = null; }
        currentUid = null;
        foods.clear();
        notifyChanged();
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

    /** item.id 를 그대로 Firestore 문서 ID 로 사용 */
    public void addFood(FoodItem item) {
        foods.add(item);          // 화면에 즉시 반영
        notifyChanged();
        if (currentUid == null) { Log.w(TAG, "로그인 전이라 서버에 저장하지 않음"); return; }

        foodsRef().document(item.id).set(toMap(item))
                .addOnFailureListener(e -> Log.e(TAG, "식품 추가 실패", e));
    }

    /** 수정: 같은 id 의 식품을 통째로 교체 */
    public void updateFood(FoodItem item) {
        for (int i = 0; i < foods.size(); i++) {
            if (foods.get(i).id.equals(item.id)) { foods.set(i, item); break; }
        }
        notifyChanged();
        if (currentUid == null) return;

        foodsRef().document(item.id).set(toMap(item), SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "식품 수정 실패", e));
    }

    public void removeFood(String id) {
        for (int i = 0; i < foods.size(); i++) {
            if (foods.get(i).id.equals(id)) { foods.remove(i); break; }
        }
        notifyChanged();
        if (currentUid == null) return;

        foodsRef().document(id).delete()
                .addOnFailureListener(e -> Log.e(TAG, "식품 삭제 실패", e));
    }

    // ---------- FoodItem <-> Firestore 변환 ----------
    private static Map<String, Object> toMap(FoodItem f) {
        Map<String, Object> m = new HashMap<>();
        m.put(F_NAME, f.name);
        m.put(F_EMOJI, f.emoji);
        m.put(F_LOCATION, f.location);
        m.put(F_QUANTITY, f.quantity);
        m.put(F_STATUS, f.status);
        m.put(F_REGISTERED, toTimestamp(f.registeredDate));
        m.put(F_EXPIRY, toTimestamp(f.expiryDate));
        m.put(F_UPDATED_AT, FieldValue.serverTimestamp());
        return m;
    }

    private static FoodItem fromDoc(DocumentSnapshot d) {
        LocalDate expiry = toLocalDate(d.getTimestamp(F_EXPIRY));
        if (expiry == null) { Log.w(TAG, "유통기한 없는 문서 건너뜀: " + d.getId()); return null; }

        LocalDate registered = toLocalDate(d.getTimestamp(F_REGISTERED));
        Long qty = d.getLong(F_QUANTITY);

        FoodItem f = new FoodItem(
                d.getId(),
                d.getString(F_NAME),
                d.getString(F_EMOJI),
                d.getString(F_LOCATION),
                qty == null ? 1 : qty.intValue(),
                registered == null ? LocalDate.now() : registered,
                expiry);

        String status = d.getString(F_STATUS);
        if (status != null) f.status = status; // 없으면 생성자 기본값 "양호"
        return f;
    }

    private static Timestamp toTimestamp(LocalDate date) {
        if (date == null) return null;
        return new Timestamp(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    private static LocalDate toLocalDate(Timestamp ts) {
        if (ts == null) return null;
        return ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ---------- 남은 요리 (더미, 담당자가 Firestore 로 교체 예정) ----------
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

    // ---------- 레시피 (더미) ----------
    public List<Recipe> getRecipes() {
        return new ArrayList<>(recipes);
    }

    public Recipe getRecipe(String id) {
        for (Recipe r : recipes) if (r.id.equals(id)) return r;
        return null;
    }

    // ---------- 더미 데이터 (식품 더미는 제거됨) ----------
    private void seed() {
        LocalDate today = LocalDate.now();

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