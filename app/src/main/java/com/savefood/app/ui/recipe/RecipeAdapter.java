package com.savefood.app.ui.recipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.savefood.app.R;
import com.savefood.app.model.Recipe;

import java.util.ArrayList;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.VH> {

    public interface OnRecipeClick {
        void onClick(Recipe recipe);
    }

    private List<Recipe> items = new ArrayList<>();
    private final OnRecipeClick listener;

    public RecipeAdapter(OnRecipeClick listener) {
        this.listener = listener;
    }

    public void submit(List<Recipe> list) {
        items = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Recipe r = items.get(position);
        h.emoji.setText(r.emoji);
        h.name.setText(r.name);
        h.have.setText("재료 " + r.haveCount() + "/" + r.totalCount() + " 보유");
        h.meta.setText("⏱ " + r.minutes + "분 · 난이도 " + r.difficulty);

        h.chips.removeAllViews();
        for (String used : r.usedFoods) {
            Chip chip = new Chip(h.itemView.getContext());
            chip.setText(used);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.red_light);
            chip.setTextColor(h.itemView.getContext().getColor(R.color.red));
            h.chips.addView(chip);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(r));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, name, have, meta;
        final ChipGroup chips;

        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.tv_emoji);
            name = v.findViewById(R.id.tv_name);
            have = v.findViewById(R.id.tv_have);
            meta = v.findViewById(R.id.tv_meta);
            chips = v.findViewById(R.id.chips_used);
        }
    }
}
