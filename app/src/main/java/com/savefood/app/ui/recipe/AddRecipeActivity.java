package com.savefood.app.ui.recipe;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.data.RecipeAiClient;
import com.savefood.app.data.RecipeRecommender;
import com.savefood.app.model.FoodItem;
import com.savefood.app.model.Recipe;

import java.util.List;

/**
 * 레시피 직접 추가.
 * 요리 이름 입력 후 "AI로 채우기"를 누르면 나머지 칸이 추정값으로 채워지고, 사용자가 확인·수정 후 저장한다.
 */
public class AddRecipeActivity extends AppCompatActivity {

    private static final String DEFAULT_EMOJI = "🍽️";
    private static final String[] EMOJIS = {"🍽️", "🍚", "🍜", "🍝", "🍲", "🍛", "🥗", "🍳", "🍗", "🥩", "🐟", "🥪", "🥘", "🍰", "🥤"};
    private static final int DEFAULT_MINUTES = 20;

    private FoodRepository repo;
    private List<FoodItem> foods;

    private TextInputLayout tilName;
    private EditText etName, etMinutes, etServings;
    private TextView tvEmoji, tvAiNotice;
    private MaterialButton btnAi;
    private ProgressBar progressAi;
    private ChipGroup chipDifficulty;
    private LinearLayout layoutIngredients, layoutSteps;
    private String emoji = DEFAULT_EMOJI;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe);

        repo = FoodRepository.get();
        foods = repo.getFoods();

        tilName = findViewById(R.id.til_name);
        etName = findViewById(R.id.et_name);
        etMinutes = findViewById(R.id.et_minutes);
        etServings = findViewById(R.id.et_servings);
        tvEmoji = findViewById(R.id.tv_emoji);
        tvAiNotice = findViewById(R.id.tv_ai_notice);
        btnAi = findViewById(R.id.btn_ai);
        progressAi = findViewById(R.id.progress_ai);
        chipDifficulty = findViewById(R.id.chip_group_difficulty);
        layoutIngredients = findViewById(R.id.layout_ingredients);
        layoutSteps = findViewById(R.id.layout_steps);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_ingredient).setOnClickListener(v -> addIngredientRow("", ""));
        findViewById(R.id.btn_add_step).setOnClickListener(v -> addStepRow("", 0, ""));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        // 키가 없으면 AI 버튼 숨김
        btnAi.setVisibility(repo.isRecipeAiAvailable() ? View.VISIBLE : View.GONE);
        btnAi.setOnClickListener(v -> fillWithAi());

        setupEmojiChips();
        setupFridgeChips();
        addIngredientRow("", "");
    }

    // ---------- 이모지 ----------
    private void setupEmojiChips() {
        ChipGroup group = findViewById(R.id.chip_group_emoji);
        for (String e : EMOJIS) {
            Chip chip = new Chip(this);
            chip.setText(e);
            chip.setTextSize(18);
            chip.setOnClickListener(v -> setEmoji(e));
            group.addView(chip);
        }
    }

    private void setEmoji(String value) {
        emoji = (value == null || value.trim().isEmpty()) ? DEFAULT_EMOJI : value.trim();
        tvEmoji.setText(emoji);
    }

    // ---------- 냉장고 재료 칩 ----------
    private void setupFridgeChips() {
        ChipGroup group = findViewById(R.id.chip_group_fridge);
        TextView hint = findViewById(R.id.tv_fridge_hint);
        if (foods.isEmpty()) {
            hint.setText("냉장고에 등록된 식품이 없어요. 재료를 직접 입력해 주세요");
            findViewById(R.id.scroll_fridge).setVisibility(View.GONE);
            return;
        }
        for (FoodItem food : foods) {
            String name = food.name.replaceAll("\\(.*?\\)", "").trim();
            if (name.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText(food.emoji == null || food.emoji.isEmpty() ? name : food.emoji + " " + name);
            chip.setOnClickListener(v -> addFridgeIngredient(name));
            group.addView(chip);
        }
    }

    private void addFridgeIngredient(String name) {
        // 이미 있으면 추가하지 않음
        for (int i = 0; i < layoutIngredients.getChildCount(); i++) {
            String existing = text(layoutIngredients.getChildAt(i).findViewById(R.id.et_ing_name));
            if (existing.equals(name)) {
                Toast.makeText(this, "이미 추가된 재료예요", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // 마지막 줄이 비어 있으면 그 줄에 채움
        int count = layoutIngredients.getChildCount();
        if (count > 0) {
            View last = layoutIngredients.getChildAt(count - 1);
            EditText lastName = last.findViewById(R.id.et_ing_name);
            if (text(lastName).isEmpty()) {
                lastName.setText(name);
                last.findViewById(R.id.et_ing_amount).requestFocus();
                return;
            }
        }
        addIngredientRow(name, "");
    }

    // ---------- 재료 줄 ----------
    private void addIngredientRow(String name, String amount) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_edit_ingredient, layoutIngredients, false);
        EditText etIngName = row.findViewById(R.id.et_ing_name);
        EditText etIngAmount = row.findViewById(R.id.et_ing_amount);
        TextView tvStatus = row.findViewById(R.id.tv_ing_status);

        etIngName.setText(name);
        etIngAmount.setText(amount);
        updateIngredientStatus(tvStatus, name);
        etIngName.addTextChangedListener(new SimpleWatcher(s -> updateIngredientStatus(tvStatus, s)));
        row.findViewById(R.id.btn_remove).setOnClickListener(v -> layoutIngredients.removeView(row));

        layoutIngredients.addView(row);
    }

    // 보유 식품과 매칭되면 "보유", 아니면 "부족"
    private void updateIngredientStatus(TextView tv, String name) {
        if (name.trim().isEmpty()) {
            tv.setText("");
            return;
        }
        boolean have = RecipeRecommender.findMatch(name, foods) != null;
        tv.setText(have ? "✓ 보유" : "부족");
        tv.setTextColor(getColor(have ? R.color.green_dark : R.color.red));
    }

    // ---------- 조리 단계 줄 ----------
    private void addStepRow(String title, int minutes, String description) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_edit_step, layoutSteps, false);
        ((EditText) row.findViewById(R.id.et_step_title)).setText(title);
        ((EditText) row.findViewById(R.id.et_step_minutes)).setText(minutes > 0 ? String.valueOf(minutes) : "");
        ((EditText) row.findViewById(R.id.et_step_desc)).setText(description);
        row.findViewById(R.id.btn_remove).setOnClickListener(v -> {
            layoutSteps.removeView(row);
            renumberSteps();
        });
        layoutSteps.addView(row);
        renumberSteps();
    }

    private void renumberSteps() {
        for (int i = 0; i < layoutSteps.getChildCount(); i++) {
            ((TextView) layoutSteps.getChildAt(i).findViewById(R.id.tv_step_no)).setText(String.valueOf(i + 1));
        }
    }

    // ---------- AI 채우기 ----------
    private void fillWithAi() {
        String name = text(etName);
        if (name.isEmpty()) {
            tilName.setError("요리 이름을 먼저 입력해 주세요");
            return;
        }
        tilName.setError(null);
        setAiLoading(true);

        repo.generateRecipeDraft(name, new RecipeAiClient.Callback() {
            @Override
            public void onSuccess(Recipe draft) {
                if (isFinishing() || isDestroyed()) return;
                setAiLoading(false);
                applyDraft(draft);
                tvAiNotice.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setAiLoading(false);
                Toast.makeText(AddRecipeActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setAiLoading(boolean loading) {
        btnAi.setEnabled(!loading);
        btnAi.setText(loading ? "AI가 채우는 중…" : "✨ AI로 채우기");
        progressAi.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // AI 결과로 이름 아래 칸을 모두 덮어씀
    private void applyDraft(Recipe draft) {
        setEmoji(draft.emoji);
        etMinutes.setText(String.valueOf(draft.minutes));
        etServings.setText(String.valueOf(draft.servings));
        chipDifficulty.check("쉬움".equals(draft.difficulty) ? R.id.chip_easy
                : "어려움".equals(draft.difficulty) ? R.id.chip_hard : R.id.chip_normal);

        layoutIngredients.removeAllViews();
        for (Recipe.Ingredient ing : draft.ingredients) addIngredientRow(ing.foodName, ing.amount);

        layoutSteps.removeAllViews();
        for (Recipe.Step step : draft.steps) addStepRow(step.title, step.minutes, step.description);
    }

    // ---------- 저장 ----------
    private void save() {
        String name = text(etName);
        if (name.isEmpty()) {
            tilName.setError("요리 이름을 입력해 주세요");
            etName.requestFocus();
            return;
        }
        tilName.setError(null);

        Recipe recipe = new Recipe();
        recipe.name = name;
        recipe.emoji = emoji;

        for (int i = 0; i < layoutIngredients.getChildCount(); i++) {
            View row = layoutIngredients.getChildAt(i);
            String ingName = text(row.findViewById(R.id.et_ing_name));
            if (ingName.isEmpty()) continue;
            recipe.ingredients.add(new Recipe.Ingredient(ingName, text(row.findViewById(R.id.et_ing_amount))));
        }
        if (recipe.ingredients.isEmpty()) {
            Toast.makeText(this, "재료를 1개 이상 입력해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        int stepSum = 0;
        for (int i = 0; i < layoutSteps.getChildCount(); i++) {
            View row = layoutSteps.getChildAt(i);
            String title = text(row.findViewById(R.id.et_step_title));
            String desc = text(row.findViewById(R.id.et_step_desc));
            if (title.isEmpty() && desc.isEmpty()) continue;
            int minutes = parseInt(text(row.findViewById(R.id.et_step_minutes)), 0);
            stepSum += minutes;
            recipe.steps.add(new Recipe.Step(title.isEmpty() ? "단계 " + (recipe.steps.size() + 1) : title, minutes, desc));
        }

        // 조리 시간이 비어 있으면 단계 시간 합 → 그것도 없으면 기본값
        int minutes = parseInt(text(etMinutes), 0);
        if (minutes <= 0) minutes = stepSum > 0 ? stepSum : DEFAULT_MINUTES;
        recipe.minutes = minutes;
        recipe.servings = Math.max(1, parseInt(text(etServings), 1));

        Chip checked = findViewById(chipDifficulty.getCheckedChipId());
        recipe.difficulty = checked != null ? checked.getText().toString() : "쉬움";

        repo.addRecipe(recipe);
        Toast.makeText(this, "레시피를 추가했어요", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- 도우미 ----------
    private static String text(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String text(View v) {
        return text((EditText) v);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private interface OnText { void changed(String s); }

    private static class SimpleWatcher implements TextWatcher {
        private final OnText onText;
        SimpleWatcher(OnText onText) { this.onText = onText; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) { onText.changed(s.toString()); }
    }
}
