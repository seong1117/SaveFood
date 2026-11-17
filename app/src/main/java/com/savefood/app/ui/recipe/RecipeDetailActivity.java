package com.savefood.app.ui.recipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.data.RecipeAiClient;
import com.savefood.app.data.RecipeRecommender;
import com.savefood.app.model.Recipe;
import com.savefood.app.model.Substitution;
import com.savefood.app.util.UiUtils;

import java.util.List;

/** 레시피 상세: 재료 보유 여부 · 부족 재료 해결 방법 · 조리 순서 · 조리 완료 */
public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "recipe_id";

    private FoodRepository repo;
    private Recipe recipe;

    private View cardSubstitute, tvSubBadge, rowFindSub;
    private TextView tvSubHint;
    private LinearLayout layoutSubstitutes;
    private MaterialButton btnFindSub;
    private ProgressBar progressSub;

    // 조리 완료 화면에서 저장하면 상세 화면도 닫음
    private final ActivityResultLauncher<Intent> cookDoneLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) finish();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        repo = FoodRepository.get();
        recipe = repo.getRecipe(getIntent().getStringExtra(EXTRA_RECIPE_ID));
        if (recipe == null) { finish(); return; }
        // Recheck have/missing with current foods / 현재 보유 식품으로 보유 여부 다시 계산
        RecipeRecommender.evaluate(recipe, repo.getFoods());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tv_emoji)).setText(recipe.emoji);
        ((TextView) findViewById(R.id.tv_name)).setText(recipe.name);
        ((TextView) findViewById(R.id.tv_meta)).setText(
                "⏱ " + recipe.minutes + "분  ·  " + recipe.servings + "인분  ·  난이도 " + recipe.difficulty);

        int missing = recipe.totalCount() - recipe.haveCount();
        ((TextView) findViewById(R.id.tv_have)).setText("✓ 보유 " + recipe.haveCount() + "개 · 부족 " + missing + "개");

        // 재료
        LinearLayout ingredients = findViewById(R.id.layout_ingredients);
        for (Recipe.Ingredient ing : recipe.ingredients) {
            TextView tv = new TextView(this);
            tv.setText((ing.have ? "✓  " : "−  ") + ing.display() + (ing.have ? "" : "  (부족)"));
            tv.setTextSize(14);
            tv.setTextColor(getColor(ing.have ? R.color.text_primary : R.color.red));
            tv.setPadding(0, UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4));
            ingredients.addView(tv);
        }

        setupSubstituteCard(missing);

        // 조리 순서
        ((TextView) findViewById(R.id.tv_step_count)).setText(recipe.steps.size() + "단계");
        LinearLayout steps = findViewById(R.id.layout_steps);
        int no = 1;
        for (Recipe.Step s : recipe.steps) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_step, steps, false);
            ((TextView) row.findViewById(R.id.tv_no)).setText(String.valueOf(no++));
            ((TextView) row.findViewById(R.id.tv_title)).setText(s.title);
            ((TextView) row.findViewById(R.id.tv_time)).setText(s.minutes + "분");
            ((TextView) row.findViewById(R.id.tv_desc)).setText(s.description);
            steps.addView(row);
        }

        // 조리 완료 → 사용한 재료 확인 → 남은 음식 보관 등록
        findViewById(R.id.btn_done).setOnClickListener(v -> {
            Intent intent = new Intent(this, CookDoneActivity.class);
            intent.putExtra(CookDoneActivity.EXTRA_RECIPE_ID, recipe.id);
            cookDoneLauncher.launch(intent);
        });
    }

    // ---------- 부족 재료 해결 방법 ----------
    private void setupSubstituteCard(int missing) {
        cardSubstitute = findViewById(R.id.card_substitute);
        tvSubBadge = findViewById(R.id.tv_sub_badge);
        tvSubHint = findViewById(R.id.tv_sub_hint);
        layoutSubstitutes = findViewById(R.id.layout_substitutes);
        rowFindSub = findViewById(R.id.row_find_sub);
        btnFindSub = findViewById(R.id.btn_find_sub);
        progressSub = findViewById(R.id.progress_sub);

        if (missing == 0) return;   // 부족 재료가 없으면 카드 숨김
        cardSubstitute.setVisibility(View.VISIBLE);

        if (!repo.isRecipeAiAvailable()) {
            // AI 키가 없으면 안내만 표시
            tvSubHint.setText("부족한 재료는 차감하지 않고 조리 완료할 수 있어요");
            rowFindSub.setVisibility(View.GONE);
            return;
        }

        btnFindSub.setOnClickListener(v -> findSubstitutes());

        // 이전에 받은 결과가 있으면 바로 표시
        List<Substitution> cached = repo.getSubstitutions(recipe.id);
        if (cached != null) showSubstitutes(cached);
    }

    private void findSubstitutes() {
        setSubLoading(true);
        repo.suggestSubstitutes(recipe, new RecipeAiClient.SubstituteCallback() {
            @Override
            public void onSuccess(List<Substitution> items) {
                if (isFinishing() || isDestroyed()) return;
                setSubLoading(false);
                showSubstitutes(items);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setSubLoading(false);
                Toast.makeText(RecipeDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setSubLoading(boolean loading) {
        btnFindSub.setEnabled(!loading);
        btnFindSub.setText(loading ? "AI가 찾는 중…" : "✨ 대체 방법 찾기");
        progressSub.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showSubstitutes(List<Substitution> items) {
        layoutSubstitutes.removeAllViews();
        if (items.isEmpty()) {
            tvSubHint.setText("해결 방법을 찾지 못했어요. 부족한 재료는 차감 없이 조리 완료할 수 있어요");
            return;
        }
        tvSubBadge.setVisibility(View.VISIBLE);
        boolean anySubstitute = false;
        for (Substitution sub : items) if (sub.isSubstitute()) anySubstitute = true;
        tvSubHint.setText(anySubstitute
                ? "대체 재료는 조리 완료 시 재고에서 차감돼요"
                : "부족한 재료는 차감하지 않고 조리 완료할 수 있어요");
        btnFindSub.setText("✨ 다시 찾기");

        for (Substitution sub : items) {
            String action;
            int color;
            if (sub.isSubstitute()) {
                action = "🔄 " + sub.use + "(으)로 대체";
                color = R.color.green_dark;
            } else if (Substitution.OMIT.equals(sub.action)) {
                action = "⏭ 빼고 만들 수 있어요";
                color = R.color.text_secondary;
            } else {
                action = "🛒 꼭 필요해요";
                color = R.color.red;
            }

            TextView title = new TextView(this);
            title.setText(sub.missing + "  →  " + action);
            title.setTextSize(14);
            title.setTextColor(getColor(color));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setPadding(0, UiUtils.dp(this, 10), 0, 0);
            layoutSubstitutes.addView(title);

            if (sub.tip != null && !sub.tip.isEmpty()) {
                TextView tip = new TextView(this);
                tip.setText(sub.tip);
                tip.setTextSize(12);
                tip.setTextColor(getColor(R.color.text_secondary));
                tip.setPadding(0, UiUtils.dp(this, 2), 0, 0);
                layoutSubstitutes.addView(tip);
            }
        }
    }
}
