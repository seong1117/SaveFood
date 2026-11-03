package com.savefood.app.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.Recipe;
import com.savefood.app.util.UiUtils;

/** 레시피 상세: 재료 보유 여부 · 조리 순서 · 조리 완료 */
public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "recipe_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        Recipe recipe = FoodRepository.get().getRecipe(getIntent().getStringExtra(EXTRA_RECIPE_ID));
        if (recipe == null) { finish(); return; }

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
            tv.setText((ing.have ? "✓  " : "−  ") + ing.name + (ing.have ? "" : "  (부족)"));
            tv.setTextSize(14);
            tv.setTextColor(getColor(ing.have ? R.color.text_primary : R.color.red));
            tv.setPadding(0, UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4));
            ingredients.addView(tv);
        }

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

        findViewById(R.id.btn_done).setOnClickListener(v -> {
            // TODO(팀원): 조리 완료 흐름 구현
            //  1) 사용한 재료 확인 화면 (수량 수정) → 재고에서 차감
            //  2) 남은 음식 보관 등록 (남은 양·보관 장소·권장 소비기한·알림) → Leftover 추가
            Toast.makeText(this, "TODO: 재료 차감 → 남은 음식 등록", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
