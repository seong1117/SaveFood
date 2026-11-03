package com.savefood.app.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 냉장고에 등록된 식품.
 * TODO(Firestore 연동): LocalDate 는 Firestore가 직접 저장하지 못하므로
 *  저장할 때 String("yyyy-MM-dd") 또는 Timestamp 로 변환하세요.
 */
public class FoodItem {
    public String id;
    public String name;       // 예: "우유 (1L)"
    public String emoji;      // 임시 썸네일 (나중에 이미지 URL 로 교체)
    public String location;   // 예: "냉장실 · 문 선반"
    public String status;     // 양호 / 주의 / 상함
    public int quantity;
    public LocalDate registeredDate;
    public LocalDate expiryDate;

    public FoodItem() { }

    public FoodItem(String id, String name, String emoji, String location, int quantity,
                    LocalDate registeredDate, LocalDate expiryDate) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.location = location;
        this.quantity = quantity;
        this.status = "양호";
        this.registeredDate = registeredDate;
        this.expiryDate = expiryDate;
    }

    /** 오늘 기준 남은 일수 (소비기한 당일이면 0, 지났으면 음수) */
    public long getDDay() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public String getDDayLabel() {
        long d = getDDay();
        if (d < 0) return "D+" + (-d);
        if (d == 0) return "D-DAY";
        return "D-" + d;
    }

    public String getRemainText() {
        long d = getDDay();
        if (d < 0) return "기한 지남";
        if (d == 0) return "오늘까지";
        return d + "일 남음";
    }

    public boolean isUrgent() {
        return getDDay() <= 1;
    }
}
