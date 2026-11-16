package com.savefood.app.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.data.RecipeRecommender;
import com.savefood.app.model.FoodItem;
import com.savefood.app.model.Leftover;
import com.savefood.app.model.Recipe;
import com.savefood.app.model.Substitution;
import com.savefood.app.util.UiUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 조리 완료 흐름 (2단계, 마지막에 한 번 더 확인).
 *  1) 사용한 재료 확인: 보유 재료만 차감(수량 수정 가능), 부족한 재료는 차감 없음
 *  2) 남은 음식 보관 등록: 남은 양 · 보관 장소 · 권장 소비 기한(1/3/5일, 기본 3일) · 알림
 * "완료"를 누를 때만 실제로 저장한다.
 */
public class CookDoneActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "recipe_id";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 차감할 보유 식품 한 줄 */
    private static class UsedRow {
        FoodItem food;
        int before;           // 조리 전 수량
        int after;            // 조리 후 남은 수량 (0 이면 삭제)
        final List<String> notes = new ArrayList<>();
        TextView tvQty, tvChange;
    }

    private FoodRepository repo;
    private Recipe recipe;
    private final Map<String, UsedRow> usedRows = new LinkedHashMap<>();   // 식품 id → 줄
    private final List<String> missingLines = new ArrayList<>();

    private int step = 1;
    private int servings = 1;

    private TextView tvTitle, tvStep, tvServings, tvSafeUntil, tvSummary;
    private View layoutStep1, layoutStep2;
    private MaterialButton btnPrimary, btnSecondary;
    private ChipGroup chipLocation, chipDays;
    private SwitchMaterial switchAlarm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cook_done);

        repo = FoodRepository.get();
        recipe = repo.getRecipe(getIntent().getStringExtra(EXTRA_RECIPE_ID));
        if (recipe == null) { finish(); return; }

        tvTitle = findViewById(R.id.tv_title);
        tvStep = findViewById(R.id.tv_step);
        layoutStep1 = findViewById(R.id.layout_step1);
        layoutStep2 = findViewById(R.id.layout_step2);
        btnPrimary = findViewById(R.id.btn_primary);
        btnSecondary = findViewById(R.id.btn_secondary);
        tvServings = findViewById(R.id.tv_servings);
        tvSafeUntil = findViewById(R.id.tv_safe_until);
        tvSummary = findViewById(R.id.tv_summary);
        chipLocation = findViewById(R.id.chip_group_location);
        chipDays = findViewById(R.id.chip_group_days);
        switchAlarm = findViewById(R.id.switch_alarm);

        ((TextView) findViewById(R.id.tv_dish)).setText(recipe.emoji + "  " + recipe.name);

        buildUsedList();
        setupLeftoverForm();

        findViewById(R.id.btn_back).setOnClickListener(v -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { goBack(); }
        });
        btnPrimary.setOnClickListener(v -> {
            if (step == 1) showStep(2);
            else complete(true);
        });
        btnSecondary.setOnClickListener(v -> complete(false));

        showStep(1);
    }

    // ---------- 1단계: 사용한 재료 ----------
    private void buildUsedList() {
        List<FoodItem> foods = repo.getFoods();
        List<Substitution> subs = repo.getSubstitutions(recipe.id);

        for (Recipe.Ingredient ing : recipe.ingredients) {
            FoodItem food = RecipeRecommender.findMatch(ing.foodName, foods);
            if (food != null) {
                addUsed(food, "레시피: " + ing.display());
                continue;
            }
            // 부족한 재료: AI 가 보유 재료로 대체를 제안했으면 그 재료를 차감
            Substitution sub = findSubstitution(subs, ing.foodName);
            FoodItem subFood = (sub != null && sub.isSubstitute()) ? repo.getFood(sub.useFoodId) : null;
            if (subFood != null) {
                addUsed(subFood, ing.foodName + " 대신 사용");
            } else {
                boolean omit = sub != null && Substitution.OMIT.equals(sub.action);
                missingLines.add("−  " + ing.display() + (omit ? "  · 빼고 조리" : "") + "  · 차감 없음");
            }
        }

        LinearLayout layoutUsed = findViewById(R.id.layout_used);
        for (UsedRow row : usedRows.values()) layoutUsed.addView(createUsedView(row, layoutUsed));
        findViewById(R.id.tv_used_empty).setVisibility(usedRows.isEmpty() ? View.VISIBLE : View.GONE);

        if (!missingLines.isEmpty()) {
            findViewById(R.id.section_missing).setVisibility(View.VISIBLE);
            LinearLayout layoutMissing = findViewById(R.id.layout_missing);
            for (String line : missingLines) {
                TextView tv = new TextView(this);
                tv.setText(line);
                tv.setTextSize(14);
                tv.setTextColor(getColor(R.color.text_secondary));
                tv.setPadding(0, UiUtils.dp(this, 3), 0, UiUtils.dp(this, 3));
                layoutMissing.addView(tv);
            }
        }
    }

    private static Substitution findSubstitution(List<Substitution> subs, String missing) {
        if (subs == null) return null;
        for (Substitution s : subs) if (missing.equals(s.missing)) return s;
        return null;
    }

    // 같은 식품이 여러 재료에 쓰이면 한 줄로 합침
    private void addUsed(FoodItem food, String note) {
        UsedRow row = usedRows.get(food.id);
        if (row == null) {
            row = new UsedRow();
            row.food = food;
            row.before = Math.max(0, food.quantity);
            row.after = Math.max(0, row.before - 1);   // 기본: 1개 사용
            usedRows.put(food.id, row);
        }
        row.notes.add(note);
    }

    private View createUsedView(UsedRow row, LinearLayout parent) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_used_food, parent, false);
        ((TextView) v.findViewById(R.id.tv_emoji)).setText(row.food.emoji == null ? "🍽️" : row.food.emoji);
        ((TextView) v.findViewById(R.id.tv_name)).setText(row.food.name);
        ((TextView) v.findViewById(R.id.tv_note)).setText(String.join("\n", row.notes));
        row.tvQty = v.findViewById(R.id.tv_qty);
        row.tvChange = v.findViewById(R.id.tv_change);
        v.findViewById(R.id.btn_minus).setOnClickListener(x -> {
            if (row.after > 0) { row.after--; updateRow(row); }
        });
        v.findViewById(R.id.btn_plus).setOnClickListener(x -> {
            if (row.after < row.before) { row.after++; updateRow(row); }
        });
        updateRow(row);
        return v;
    }

    private void updateRow(UsedRow row) {
        row.tvQty.setText(String.valueOf(row.after));
        row.tvChange.setText(changeText(row));
        int color = row.after == 0 ? R.color.red : row.after == row.before ? R.color.text_secondary : R.color.green_dark;
        row.tvChange.setTextColor(getColor(color));
    }

    private static String changeText(UsedRow row) {
        if (row.after == row.before) return "보유 " + row.before + "개 · 그대로";
        if (row.after == 0) return "보유 " + row.before + "개 → 다 사용 (냉장고에서 삭제)";
        return "보유 " + row.before + "개 → " + row.after + "개 남음";
    }

    // ---------- 2단계: 남은 음식 ----------
    private void setupLeftoverForm() {
        servings = 1;
        findViewById(R.id.btn_servings_minus).setOnClickListener(v -> {
            if (servings > 1) { servings--; updateLeftoverViews(); }
        });
        findViewById(R.id.btn_servings_plus).setOnClickListener(v -> {
            if (servings < 10) { servings++; updateLeftoverViews(); }
        });
        chipLocation.setOnCheckedStateChangeListener((g, ids) -> updateLeftoverViews());
        chipDays.setOnCheckedStateChangeListener((g, ids) -> updateLeftoverViews());
        switchAlarm.setOnCheckedChangeListener((b, checked) -> updateLeftoverViews());
        updateLeftoverViews();
    }

    private int selectedDays() {
        int id = chipDays.getCheckedChipId();
        return id == R.id.chip_day1 ? 1 : id == R.id.chip_day5 ? 5 : 3;
    }

    private String selectedLocation() {
        Chip chip = findViewById(chipLocation.getCheckedChipId());
        return chip != null ? chip.getText().toString() : "냉장실";
    }

    private void updateLeftoverViews() {
        tvServings.setText(servings + "인분");
        tvSafeUntil.setText(LocalDate.now().plusDays(selectedDays()).format(DATE) + "까지");
        tvSummary.setText(buildSummary());
    }

    // 저장 전 확인 문구 (double check)
    private String buildSummary() {
        StringBuilder sb = new StringBuilder("재료 차감\n");
        boolean anyChange = false;
        for (UsedRow row : usedRows.values()) {
            if (row.after == row.before) continue;
            anyChange = true;
            sb.append("  • ").append(row.food.name).append("  ").append(row.before).append(" → ").append(row.after)
                    .append(row.after == 0 ? " (삭제)" : "").append('\n');
        }
        if (!anyChange) sb.append("  • 차감 없음\n");
        if (!missingLines.isEmpty()) {
            sb.append("  • 부족했던 재료 ").append(missingLines.size()).append("개는 차감 없음\n");
        }
        sb.append("\n남은 음식\n  • ").append(recipe.name).append(" ").append(servings).append("인분 · ")
                .append(selectedLocation()).append(" · ")
                .append(LocalDate.now().plusDays(selectedDays()).format(DATE)).append("까지 · 알림 ")
                .append(switchAlarm.isChecked() ? "켬" : "끔");
        return sb.toString();
    }

    // ---------- 단계 이동 ----------
    private void showStep(int newStep) {
        step = newStep;
        boolean first = step == 1;
        layoutStep1.setVisibility(first ? View.VISIBLE : View.GONE);
        layoutStep2.setVisibility(first ? View.GONE : View.VISIBLE);
        tvTitle.setText(first ? "사용한 재료 확인" : "남은 음식 보관 등록");
        tvStep.setText(step + " / 2");
        btnPrimary.setText(first ? "다음" : "완료");
        btnSecondary.setVisibility(first ? View.GONE : View.VISIBLE);
        if (!first) updateLeftoverViews();
    }

    private void goBack() {
        if (step == 2) showStep(1);
        else finish();   // 아무것도 저장하지 않고 닫음
    }

    // ---------- 저장 ----------
    private void complete(boolean withLeftover) {
        Map<String, Integer> changes = new HashMap<>();
        for (UsedRow row : usedRows.values()) {
            if (row.after != row.before) changes.put(row.food.id, row.after);
        }

        Leftover leftover = null;
        if (withLeftover) {
            LocalDate today = LocalDate.now();
            leftover = new Leftover(UUID.randomUUID().toString(), recipe.name, recipe.emoji, servings,
                    today, today.plusDays(selectedDays()));
            leftover.location = selectedLocation();
            leftover.alarmOn = switchAlarm.isChecked();   // 실제 알림 예약은 알림 기능에서 처리
        }

        repo.completeCooking(recipe.id, changes, leftover);
        Toast.makeText(this, withLeftover ? "조리 완료! 남은 음식을 등록했어요" : "조리 완료! 재료를 정리했어요",
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
