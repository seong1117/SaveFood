package com.savefood.app.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.chip.Chip;
import com.savefood.app.R;
import com.savefood.app.ui.scan.FoodCatalog;

/**
 * 식재료 아이콘 보여 주기. 아이콘은 저장된 값이 아니라 이름으로 정한다(FoodCatalog.find).
 * 그래서 예전에 카트(🛒)로 저장된 품목도 이름이 맞으면 새 아이콘으로 보인다.
 * 이모지가 없는 품목(두부)은 직접 그린 그림을 쓴다.
 */
public final class FoodIcons {

    /** 이모지 글자보다 조금 크게(이모지는 글자 크기보다 약간 크게 그려진다) */
    private static final float ICON_SCALE = 1.2f;

    private FoodIcons() { }

    /** 아이콘 칸(TextView)에 이름에 맞는 아이콘을 넣는다. 모르는 품목이면 저장된 이모지, 그것도 없으면 카트. */
    public static void show(@NonNull TextView tv, @Nullable String name, @Nullable String saved) {
        tv.setText(icon(tv.getContext(), name, saved, tv.getTextSize()));
    }

    /** 칩에 아이콘과 글자를 넣는다. 그림 아이콘은 칩 아이콘 자리에, 이모지는 글자 앞에 둔다. */
    public static void chip(@NonNull Chip chip, @Nullable String name, @NonNull String text) {
        Drawable d = drawable(chip.getContext(), FoodCatalog.find(name));
        if (d != null) {
            chip.setChipIcon(d);
            chip.setChipIconVisible(true);
            chip.setText(text);
        } else {
            chip.setText(FoodCatalog.emojiFor(name) + " " + text);
        }
    }

    @NonNull
    private static CharSequence icon(Context c, @Nullable String name, @Nullable String saved, float sizePx) {
        FoodCatalog.Entry e = FoodCatalog.find(name);
        if (e == null) return saved == null || saved.isEmpty() ? FoodCatalog.DEFAULT_EMOJI : saved;
        Drawable d = drawable(c, e);
        if (d == null) return e.emoji;
        int size = Math.round(sizePx * ICON_SCALE);
        d.setBounds(0, 0, size, size);
        SpannableString text = new SpannableString("￼");
        text.setSpan(new ImageSpan(d, DynamicDrawableSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** 품목의 그림 아이콘. 없으면 null(이모지를 쓴다). */
    @Nullable
    private static Drawable drawable(Context c, @Nullable FoodCatalog.Entry e) {
        if (e == null || e.icon == null) return null;
        int res = "tofu".equals(e.icon) ? R.drawable.ic_food_tofu : 0;
        return res == 0 ? null : AppCompatResources.getDrawable(c, res);
    }
}
