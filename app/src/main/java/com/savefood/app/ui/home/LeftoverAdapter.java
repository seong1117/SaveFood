package com.savefood.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.savefood.app.R;
import com.savefood.app.model.Leftover;

import java.util.ArrayList;
import java.util.List;

public class LeftoverAdapter extends RecyclerView.Adapter<LeftoverAdapter.VH> {

    public interface OnLeftoverClick {
        void onClick(Leftover item);
    }

    private List<Leftover> items = new ArrayList<>();
    private final OnLeftoverClick listener;

    public LeftoverAdapter(OnLeftoverClick listener) {
        this.listener = listener;
    }

    public void submit(List<Leftover> list) {
        items = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leftover, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Leftover item = items.get(position);
        h.emoji.setText(item.emoji);
        h.name.setText(item.name);
        h.remain.setText(item.getRemainText());
        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, name, remain;

        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.tv_emoji);
            name = v.findViewById(R.id.tv_name);
            remain = v.findViewById(R.id.tv_remain);
        }
    }
}
