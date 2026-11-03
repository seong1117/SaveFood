package com.savefood.app.util;

import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.savefood.app.R;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class UiUtils {
    private UiUtils() { }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static String formatDate(LocalDate date) {
        return date.format(DATE);
    }

    /** D-day 배지: 1일 이하면 빨강, 그 외 초록 */
    public static void styleBadge(TextView badge, long dDay) {
        boolean urgent = dDay <= 1;
        badge.setBackgroundResource(urgent ? R.drawable.bg_badge_red : R.drawable.bg_badge_green);
        badge.setTextColor(ContextCompat.getColor(badge.getContext(),
                urgent ? R.color.red : R.color.green_dark));
    }

    public static int dp(android.content.Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
