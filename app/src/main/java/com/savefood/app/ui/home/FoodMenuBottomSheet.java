package com.savefood.app.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.FoodItem;
import com.savefood.app.util.UiUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 홈에서 식품을 눌렀을 때 나오는 메뉴: 보관 기한 추적 · 상태 정보 · 액션 */
public class FoodMenuBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onFoodConsumed(String foodId);
        void onFoodDiscarded(String foodId);
        void onShowRecipes(String foodId);
    }

    private static final String ARG_ID = "food_id";

    public static FoodMenuBottomSheet newInstance(String foodId) {
        FoodMenuBottomSheet s = new FoodMenuBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ID, foodId);
        s.setArguments(b);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_food_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String id = requireArguments().getString(ARG_ID);
        FoodItem food = FoodRepository.get().getFood(id);
        if (food == null) { dismiss(); return; }
        Listener listener = getParentFragment() instanceof Listener ? (Listener) getParentFragment() : null;

        ((TextView) v.findViewById(R.id.tv_emoji)).setText(food.emoji);
        ((TextView) v.findViewById(R.id.tv_name)).setText(food.name);
        ((TextView) v.findViewById(R.id.tv_location)).setText(food.location);
        TextView badge = v.findViewById(R.id.tv_badge);
        badge.setText(food.getRemainText());
        UiUtils.styleBadge(badge, food.getDDay());

        // 보관 기한 추적
        TextView tvExpiry = v.findViewById(R.id.tv_expiry);
        tvExpiry.setText("⏰ 소비기한 " + UiUtils.formatDate(food.expiryDate));
        tvExpiry.setTextColor(requireContext().getColor(food.isUrgent() ? R.color.red : R.color.green_dark));

        LinearProgressIndicator progress = v.findViewById(R.id.progress);
        progress.setProgress(progressPercent(food.registeredDate, food.expiryDate));
        progress.setIndicatorColor(requireContext().getColor(food.isUrgent() ? R.color.red : R.color.green));
        progress.setTrackColor(Color.parseColor("#E5E7EB"));

        ((TextView) v.findViewById(R.id.tv_registered)).setText("등록일\n" + UiUtils.formatDate(food.registeredDate));
        ((TextView) v.findViewById(R.id.tv_today)).setText("현재\n" + UiUtils.formatDate(LocalDate.now()));
        ((TextView) v.findViewById(R.id.tv_expiry_date)).setText("소비기한\n" + UiUtils.formatDate(food.expiryDate));

        // 상태 정보 (양호 / 주의 / 상함)
        ChipGroup chips = v.findViewById(R.id.chips_status);
        if ("주의".equals(food.status)) chips.check(R.id.chip_warn);
        else if ("상함".equals(food.status)) chips.check(R.id.chip_bad);
        else chips.check(R.id.chip_good);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int checked = ids.get(0);
            food.status = checked == R.id.chip_warn ? "주의" : checked == R.id.chip_bad ? "상함" : "양호";
            // TODO(팀원): 상태 변경을 Firestore 에 저장
        });

        // 액션
        v.findViewById(R.id.btn_close).setOnClickListener(x -> dismiss());
        v.findViewById(R.id.btn_recipe).setOnClickListener(x -> {
            if (listener != null) listener.onShowRecipes(food.id);
            dismiss();
        });
        v.findViewById(R.id.btn_consumed).setOnClickListener(x -> {
            if (listener != null) listener.onFoodConsumed(food.id);
            dismiss();
        });
        v.findViewById(R.id.btn_edit).setOnClickListener(x ->
                Toast.makeText(requireContext(), "TODO: 수량·기한 수정 다이얼로그", Toast.LENGTH_SHORT).show());
        v.findViewById(R.id.btn_discard).setOnClickListener(x -> {
            if (listener != null) listener.onFoodDiscarded(food.id);
            dismiss();
        });
    }

    /** 등록일~기한 사이에서 오늘까지 지난 비율(0~100) */
    static int progressPercent(LocalDate from, LocalDate to) {
        long total = Math.max(1, ChronoUnit.DAYS.between(from, to));
        long passed = ChronoUnit.DAYS.between(from, LocalDate.now());
        return (int) Math.max(0, Math.min(100, passed * 100 / total));
    }
}
