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

import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.Recipe;

import java.util.List;

/**
 * 레시피 추천 목록.
 * TODO(팀원): 보유 식재료·소비기한을 기준으로 추천 순서를 계산하세요
 *  (임박 재료를 많이 쓰는 레시피 우선, 부족 재료 적은 순 등).
 */
public class RecipeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recipe, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<Recipe> recipes = FoodRepository.get().getRecipes();
        ((TextView) view.findViewById(R.id.tv_count)).setText("맞춤 추천 " + recipes.size() + "개");

        RecyclerView rv = view.findViewById(R.id.rv_recipe);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        RecipeAdapter adapter = new RecipeAdapter(recipe -> {
            Intent intent = new Intent(requireContext(), RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
            startActivity(intent);
        });
        rv.setAdapter(adapter);
        adapter.submit(recipes);
    }
}
