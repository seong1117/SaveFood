package com.savefood.app.notification;

import android.content.Context;
import android.content.SharedPreferences;

/** 유통기한 알림 시각 설정 (기본값: 자정 00:00). 기기에 저장됨 */
public final class NotificationSettings {

    private static final String PREFS = "notification_settings";
    private static final String KEY_HOUR = "notify_hour";
    private static final String KEY_MINUTE = "notify_minute";

    private NotificationSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getHour(Context c) { return prefs(c).getInt(KEY_HOUR, 0); }

    public static int getMinute(Context c) { return prefs(c).getInt(KEY_MINUTE, 0); }

    public static void setTime(Context c, int hour, int minute) {
        prefs(c).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply();
    }

    /** 화면 표시용. 예: "자정 (오전 12:00)", "오후 9:30" */
    public static String formatTime(int hour, int minute) {
        if (hour == 0 && minute == 0) return "자정 (오전 12:00)";
        String ampm = hour < 12 ? "오전" : "오후";
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%s %d:%02d", ampm, h12, minute);
    }
}