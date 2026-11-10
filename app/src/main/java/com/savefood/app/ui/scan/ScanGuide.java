package com.savefood.app.ui.scan;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.savefood.app.util.UiUtils;

/**
 * 스캔 화면 사용 가이드.
 * 스캔 화면에 들어올 때마다 보여 주고, "다시 보지 않기"를 체크하면 그 뒤로는 숨긴다.
 * 제목 옆 "사용법" 버튼으로는 언제든 다시 볼 수 있다.
 */
final class ScanGuide {

    private static final String PREFS = "scan_guide";
    private static final String KEY_HIDE = "hide";

    private static final String TEXT =
            "촬영 모드를 고른 뒤 아래 촬영 버튼을 누르세요. 저장된 사진은 \"🖼 사진\"으로 불러올 수 있어요.\n\n"
            + "📷 식재료\n포장 없는 채소·과일을 가운데에 놓고 찍어요. 맞지 않으면 아래 품목 칩에서 골라요.\n\n"
            + "🏷 가격표\n마트 진열대 가격표 하나가 화면에 꽉 차게 찍으면 품명을 읽어요.\n\n"
            + "🧾 영수증\n영수증 전체를 찍으면 품목을 골라 한꺼번에 등록해요.\n\n"
            + "📅 소비기한\n① 포장지의 날짜를 찍고 ② 제품 이름을 찍어요. "
            + "이름이 날짜와 같은 면에 없어도 괜찮아요. 이름은 \"이름 직접 입력\"으로 건너뛸 수 있어요.\n\n"
            + "인식 결과는 추정이에요. 아래 칸에서 확인하고 고친 뒤 등록하세요.";

    private ScanGuide() { }

    static void showIfNeeded(@NonNull Context context) {
        if (!prefs(context).getBoolean(KEY_HIDE, false)) show(context);
    }

    static void show(@NonNull Context context) {
        int pad = UiUtils.dp(context, 24);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, UiUtils.dp(context, 8), pad, 0);

        TextView body = new TextView(context);
        body.setText(TEXT);
        body.setTextSize(14);
        body.setLineSpacing(0, 1.15f);
        box.addView(body);

        CheckBox hide = new CheckBox(context);
        hide.setText("다시 보지 않기");
        hide.setChecked(prefs(context).getBoolean(KEY_HIDE, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(context, 12);
        box.addView(hide, lp);

        new MaterialAlertDialogBuilder(context)
                .setTitle("스캔 사용법")
                .setView(box)
                .setPositiveButton("확인", null)
                .setOnDismissListener(d ->
                        prefs(context).edit().putBoolean(KEY_HIDE, hide.isChecked()).apply())
                .show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
