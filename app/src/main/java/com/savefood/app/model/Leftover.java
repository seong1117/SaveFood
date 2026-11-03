package com.savefood.app.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 조리하고 남은 음식. */
public class Leftover {
    public String id;
    public String name;
    public String emoji;
    public int servings;
    public LocalDate cookedDate;
    public LocalDate safeUntil;   // 안전 보관 기한
    public boolean alarmOn = true;

    public Leftover() { }

    public Leftover(String id, String name, String emoji, int servings,
                    LocalDate cookedDate, LocalDate safeUntil) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.servings = servings;
        this.cookedDate = cookedDate;
        this.safeUntil = safeUntil;
    }

    public long getDDay() {
        return ChronoUnit.DAYS.between(LocalDate.now(), safeUntil);
    }

    public String getRemainText() {
        long d = getDDay();
        if (d < 0) return "기한 지남";
        if (d == 0) return "오늘까지";
        return d + "일 남음";
    }
}
