package com.savefood.app.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.savefood.app.MainActivity;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.FoodItem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 하루 한 번 실행: D-1 ~ D-3 식재료를 모아 요약 알림 1개를 보낸다.
 * 예) 유통기한 임박 식품 3개
 *     D-1 · 우유, 연어 필렛
 *     D-3 · 아보카도
 */
public class DailyExpiryWorker extends Worker {

    public static final String KEY_SCHEDULED_AT = "scheduledAt";

    private static final String TAG = "DailyExpiryWorker";
    private static final String CHANNEL_ID = "expiry_digest";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_DDAY = 3;   // D-1, D-2, D-3 알림

    public DailyExpiryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        // 다음 날 알림을 먼저 예약 (아래에서 실패해도 매일 알림은 계속 유지)
        DailyExpiryScheduler.scheduleNext(ctx);

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();   // 로그아웃 상태면 보내지 않음

        // 예약된 시각 기준으로 D-Day 계산 (실행이 몇 분 늦어져도 D-Day 가 바뀌지 않게)
        long scheduledAtMs = getInputData().getLong(KEY_SCHEDULED_AT, System.currentTimeMillis());
        LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMs), ZoneId.systemDefault());

        List<FoodItem> foods = loadFoods(user.getUid());
        if (foods == null) return Result.success();

        // D-1 ~ D-3 별로 이름 모으기
        List<List<String>> byDDay = new ArrayList<>();
        for (int i = 0; i <= MAX_DDAY; i++) byDDay.add(new ArrayList<>());
        int count = 0;
        for (FoodItem f : foods) {
            long d = f.getDDayAt(at);
            if (d >= 1 && d <= MAX_DDAY) {
                byDDay.get((int) d).add(f.name);
                count++;
            }
        }
        if (count == 0) return Result.success();   // 임박 식품이 없으면 알림 안 보냄

        notify(ctx, count, byDDay);
        return Result.success();
    }

    /** Firestore 에서 식재료 목록을 읽음. 서버 실패 시 기기 캐시 사용 */
    private List<FoodItem> loadFoods(String uid) {
        CollectionReference col = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("foods");
        QuerySnapshot snap;
        try {
            snap = Tasks.await(col.get(), 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            try {
                snap = Tasks.await(col.get(Source.CACHE), 10, TimeUnit.SECONDS);
            } catch (Exception e2) {
                Log.e(TAG, "식재료 목록을 불러오지 못함", e2);
                return null;
            }
        }
        List<FoodItem> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            FoodItem f = FoodRepository.fromDoc(d);
            if (f != null) list.add(f);
        }
        return list;
    }

    private void notify(Context ctx, int count, List<List<String>> byDDay) {
        createChannel(ctx);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        List<String> lines = new ArrayList<>();
        for (int d = 1; d <= MAX_DDAY; d++) {
            if (byDDay.get(d).isEmpty()) continue;
            String line = "D-" + d + " · " + String.join(", ", byDDay.get(d));
            lines.add(line);
            style.addLine(line);
        }

        Intent intent = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("유통기한 임박 식품 " + count + "개")
                .setContentText(String.join(" / ", lines))
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // 알림 권한이 중간에 꺼진 경우
        }
    }

    /** 중요도 HIGH 채널이어야 화면 위에 팝업으로 뜸 */
    private static void createChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "유통기한 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("유통기한이 3일 이내로 남은 식재료를 하루 한 번 알려줘요.");
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}