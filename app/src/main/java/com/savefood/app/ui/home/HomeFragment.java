package com.savefood.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.savefood.app.MainActivity;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.FoodItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 홈: 임박 식품 · 남은 요리 · 소비기한별 식품 목록 */
public class HomeFragment extends Fragment
        implements FoodMenuBottomSheet.Listener, LeftoverBottomSheet.Listener {

    private final FoodRepository repo = FoodRepository.get();

    private FoodAdapter foodAdapter;
    private LeftoverAdapter leftoverAdapter;
    private ChipGroup chipsUrgent;
    private View cardUrgent;
    private RecyclerView rvLeftover;
    private TextView tvLeftoverEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvDate = view.findViewById(R.id.tv_date);
        tvDate.setText(new SimpleDateFormat("yyyy.MM.dd EEEE", Locale.KOREA).format(new Date()));

        chipsUrgent = view.findViewById(R.id.chips_urgent);
        cardUrgent = view.findViewById(R.id.card_urgent);
        tvLeftoverEmpty = view.findViewById(R.id.tv_leftover_empty);

        // 남은 요리 (가로 스크롤)
        rvLeftover = view.findViewById(R.id.rv_leftover);
        rvLeftover.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        leftoverAdapter = new LeftoverAdapter(item ->
                LeftoverBottomSheet.newInstance(item.id).show(getChildFragmentManager(), "leftover_menu"));
        rvLeftover.setAdapter(leftoverAdapter);

        // 식품 목록
        RecyclerView rvFood = view.findViewById(R.id.rv_food);
        rvFood.setLayoutManager(new LinearLayoutManager(requireContext()));
        foodAdapter = new FoodAdapter(item ->
                FoodMenuBottomSheet.newInstance(item.id).show(getChildFragmentManager(), "food_menu"));
        rvFood.setAdapter(foodAdapter);

        view.findViewById(R.id.fab_scan).setOnClickListener(v ->
                ((MainActivity) requireActivity()).switchTab(R.id.nav_scan));
        view.findViewById(R.id.btn_bell).setOnClickListener(v ->
                Toast.makeText(requireContext(), "TODO: 알림 센터", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.tv_leftover_all).setOnClickListener(v ->
                Toast.makeText(requireContext(), "TODO: 남은 음식 전체 목록 화면", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        foodAdapter.submit(repo.getFoods());
        leftoverAdapter.submit(repo.getLeftovers());

        boolean noLeftover = repo.getLeftovers().isEmpty();
        rvLeftover.setVisibility(noLeftover ? View.GONE : View.VISIBLE);
        tvLeftoverEmpty.setVisibility(noLeftover ? View.VISIBLE : View.GONE);

        // 임박 식품 칩
        chipsUrgent.removeAllViews();
        List<FoodItem> urgent = repo.getUrgentFoods();
        cardUrgent.setVisibility(urgent.isEmpty() ? View.GONE : View.VISIBLE);
        for (FoodItem f : urgent) {
            Chip chip = new Chip(requireContext());
            chip.setText(f.name + " " + f.getRemainText());
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(requireContext().getColor(R.color.red));
            chipsUrgent.addView(chip);
        }
    }

    // ----- FoodMenuBottomSheet.Listener -----
    @Override
    public void onFoodConsumed(String foodId) {
        repo.removeFood(foodId);   // TODO: 소비 이력(통계)으로 기록
        refresh();
        Toast.makeText(requireContext(), "소비 완료로 처리했어요", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFoodDiscarded(String foodId) {
        repo.removeFood(foodId);   // TODO: 폐기 이력(통계)으로 기록
        refresh();
        Toast.makeText(requireContext(), "폐기 처리했어요", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onShowRecipes(String foodId) {
        ((MainActivity) requireActivity()).switchTab(R.id.nav_recipe);
    }

    // ----- LeftoverBottomSheet.Listener -----
    @Override
    public void onLeftoverFinished(String leftoverId) {
        repo.removeLeftover(leftoverId);
        refresh();
        Toast.makeText(requireContext(), "잘 먹었어요! 🎉", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLeftoverDiscarded(String leftoverId) {
        repo.removeLeftover(leftoverId);
        refresh();
        Toast.makeText(requireContext(), "폐기 처리했어요", Toast.LENGTH_SHORT).show();
    }
}
