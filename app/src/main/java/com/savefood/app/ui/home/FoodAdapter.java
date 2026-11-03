package com.savefood.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.savefood.app.R;
import com.savefood.app.model.FoodItem;
import com.savefood.app.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.VH> {

    public interface OnFoodClick {
        void onClick(FoodItem item);
    }

    private List<FoodItem> items = new ArrayList<>();
    private final OnFoodClick listener;

    public FoodAdapter(OnFoodClick listener) {
        this.listener = listener;
    }

    public void submit(List<FoodItem> list) {
        items = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FoodItem item = items.get(position);
        h.emoji.setText(item.emoji);
        h.name.setText(item.name);
        h.location.setText(item.location);
        h.badge.setText(item.getDDayLabel());
        UiUtils.styleBadge(h.badge, item.getDDay());
        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, name, location, badge;

        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.tv_emoji);
            name = v.findViewById(R.id.tv_name);
            location = v.findViewById(R.id.tv_location);
            badge = v.findViewById(R.id.tv_badge);
        }
    }
}
