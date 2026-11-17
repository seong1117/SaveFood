package com.savefood.app.ui.recipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.data.RecipeRecommender;
import com.savefood.app.model.Recipe;

import java.util.List;

/**
 * Recipe list sorted by owned / expiring foods / 보유·임박 재료 기준 레시피 추천 목록
 */
public class RecipeFragment extends Fragment {

    private RecipeAdapter adapter;
    private TextView tvCount;
    private RecipeRecommender.Sort sort = RecipeRecommender.Sort.URGENT_FIRST;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recipe, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCount = view.findViewById(R.id.tv_count);

        RecyclerView rv = view.findViewById(R.id.rv_recipe);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecipeAdapter(recipe -> {
            Intent intent = new Intent(requireContext(), RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        // 레시피 직접 추가 (저장 후 돌아오면 onResume 에서 갱신)
        view.findViewById(R.id.fab_add_recipe).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddRecipeActivity.class)));

        // Filter chips -> sort type / 필터 칩 -> 정렬 기준
        ChipGroup group = view.findViewById(R.id.chip_group_filter);
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            sort = checkedId == R.id.chip_quick ? RecipeRecommender.Sort.QUICK
                    : checkedId == R.id.chip_least_missing ? RecipeRecommender.Sort.LEAST_MISSING
                    : RecipeRecommender.Sort.URGENT_FIRST;
            refresh();
        });
    }

    // Refresh after scan / 스캔 후 돌아오면 갱신
    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    // Recalculate and show list / 다시 계산해서 목록 표시
    private void refresh() {
        FoodRepository repo = FoodRepository.get();
        List<Recipe> list = RecipeRecommender.recommend(repo.getRecipes(), repo.getFoods(), sort);
        tvCount.setText("맞춤 추천 " + list.size() + "개");
        adapter.submit(list);
    }
}
