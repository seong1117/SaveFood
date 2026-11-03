package com.savefood.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.Leftover;
import com.savefood.app.util.UiUtils;

/** 홈에서 남은 요리를 눌렀을 때 나오는 메뉴: 안전 보관 기한 추적 · 재조리 가이드 · 액션 */
public class LeftoverBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onLeftoverFinished(String leftoverId);
        void onLeftoverDiscarded(String leftoverId);
    }

    private static final String ARG_ID = "leftover_id";

    public static LeftoverBottomSheet newInstance(String leftoverId) {
        LeftoverBottomSheet s = new LeftoverBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ID, leftoverId);
        s.setArguments(b);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_leftover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String id = requireArguments().getString(ARG_ID);
        Leftover item = FoodRepository.get().getLeftover(id);
        if (item == null) { dismiss(); return; }
        Listener listener = getParentFragment() instanceof Listener ? (Listener) getParentFragment() : null;

        ((TextView) v.findViewById(R.id.tv_emoji)).setText(item.emoji);
        ((TextView) v.findViewById(R.id.tv_name)).setText(item.name);
        ((TextView) v.findViewById(R.id.tv_cooked)).setText("조리일 " + UiUtils.formatDate(item.cookedDate));
        ((TextView) v.findViewById(R.id.tv_servings)).setText("남은 양 " + item.servings + "인분");
        TextView badge = v.findViewById(R.id.tv_badge);
        badge.setText(item.getRemainText());
        UiUtils.styleBadge(badge, item.getDDay());

        // 안전 보관 기한
        ((TextView) v.findViewById(R.id.tv_safe)).setText(
                "냉장 보관 · " + UiUtils.formatDate(item.safeUntil) + "까지 안전");
        LinearProgressIndicator progress = v.findViewById(R.id.progress);
        progress.setProgress(FoodMenuBottomSheet.progressPercent(item.cookedDate, item.safeUntil));
        ((TextView) v.findViewById(R.id.tv_from)).setText("조리 · " + UiUtils.formatDate(item.cookedDate));
        ((TextView) v.findViewById(R.id.tv_to)).setText(UiUtils.formatDate(item.safeUntil));

        SwitchMaterial alarm = v.findViewById(R.id.switch_alarm);
        alarm.setChecked(item.alarmOn);
        alarm.setOnCheckedChangeListener((b, checked) -> {
            item.alarmOn = checked;
            // TODO(팀원): 알림 예약/취소 (WorkManager 또는 AlarmManager)
        });

        // 재조리 가이드 (임시 정적 데이터)
        LinearLayout reuse = v.findViewById(R.id.layout_reuse);
        addReuseCard(reuse, "🍝", "파스타로 재조리", "15분");
        addReuseCard(reuse, "🥪", "샌드위치", "10분");
        addReuseCard(reuse, "🍚", "볶음밥", "8분");

        // 액션
        v.findViewById(R.id.btn_close).setOnClickListener(x -> dismiss());
        v.findViewById(R.id.btn_finished).setOnClickListener(x -> {
            if (listener != null) listener.onLeftoverFinished(item.id);
            dismiss();
        });
        v.findViewById(R.id.btn_edit).setOnClickListener(x ->
                Toast.makeText(requireContext(), "TODO: 보관 정보 수정", Toast.LENGTH_SHORT).show());
        v.findViewById(R.id.btn_discard).setOnClickListener(x -> {
            if (listener != null) listener.onLeftoverDiscarded(item.id);
            dismiss();
        });
    }

    private void addReuseCard(LinearLayout parent, String emoji, String title, String time) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_reuse, parent, false);
        ((TextView) card.findViewById(R.id.tv_emoji)).setText(emoji);
        ((TextView) card.findViewById(R.id.tv_title)).setText(title);
        ((TextView) card.findViewById(R.id.tv_time)).setText(time);
        card.setOnClickListener(x ->
                Toast.makeText(requireContext(), "TODO: 재조리 레시피 상세", Toast.LENGTH_SHORT).show());
        parent.addView(card);
    }
}
