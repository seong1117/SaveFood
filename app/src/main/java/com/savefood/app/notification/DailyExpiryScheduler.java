package com.savefood.app.notification;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 매일 설정한 시각(기본 자정)에 유통기한 요약 알림 1개를 보내도록 예약.
 * 알림이 실행되면 Worker 가 다음 날 같은 시각으로 다시 예약한다.
 */
public final class DailyExpiryScheduler {

    private static final String WORK_NAME = "daily_expiry_digest";

    private DailyExpiryScheduler() {}

    /** 다음 알림 시각으로 (다시) 예약. 앱 시작 시, 알림 시각 변경 시 호출 */
    public static void scheduleNext(Context context) {
        Context ctx = context.getApplicationContext();
        int hour = NotificationSettings.getHour(ctx);
        int minute = NotificationSettings.getMinute(ctx);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(hour, minute);
        if (!next.isAfter(now)) next = next.plusDays(1);   // 오늘 시각이 지났으면 내일

        long delayMs = Duration.between(now, next).toMillis();
        long scheduledAtMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailyExpiryWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder()
                        .putLong(DailyExpiryWorker.KEY_SCHEDULED_AT, scheduledAtMs)
                        .build())
                .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}