package com.savefood.app.model;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 냉장고에 등록된 식품.
 * Firestore 저장 시 날짜는 FoodRepository 에서 Timestamp 로 변환됨.
 * D-Day 는 유통기한 날 자정(다음날 00:00)까지 남은 시간 ÷ 24시간으로 계산.
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

    /** 유통기한 날 자정에 만료 (예: 11/22 → 11/23 00:00) */
    private LocalDateTime getExpiryDateTime() {
        return expiryDate.plusDays(1).atStartOfDay();
    }

    /** 만료까지 남은 시간(분). 지났으면 음수 */
    private long getRemainMinutes() {
        return Duration.between(LocalDateTime.now(), getExpiryDateTime()).toMinutes();
    }

    /** 남은 시간 ÷ 24시간 (내림). 24시간 미만이면 0, 만료 후엔 음수 */
    public long getDDay() {
        return Math.floorDiv(getRemainMinutes(), 24L * 60);
    }

    /** 만료 시각(유통기한 날 자정)이 지났는지 */
    public boolean isExpired() {
        return getRemainMinutes() < 0;
    }

    /** 목록 배지: 기한이 지나면 "유통기한 지남" */
    public String getDDayLabel() {
        if (isExpired()) return "유통기한 지남";
        return "D-" + getDDay();
    }

    public String getRemainText() {
        long minutes = getRemainMinutes();

        if (minutes < 0) return "유통기한 지남";
        if (minutes <= 24 * 60) {   // 24시간 이하 → 시간·분으로 표시
            return (minutes / 60) + "시간 " + (minutes % 60) + "분 남음";
        }
        return getDDay() + "일 남음";
    }
    /** 만료 시각 표시용. 예: "2026.11.17 24:00" (= 11/18 00:00) */
    public String getExpiredAtText() {
        return String.format("%d.%02d.%02d 24:00",
                expiryDate.getYear(), expiryDate.getMonthValue(), expiryDate.getDayOfMonth());
    }

    /** 만료된 지 얼마나 지났는지. 24시간 미만이면 "5시간 20분 전", 이상이면 "2일 3시간 전" */
    public String getExpiredAgoText() {
        long over = Math.max(0, -getRemainMinutes());
        if (over < 24 * 60) return (over / 60) + "시간 " + (over % 60) + "분 전";
        return (over / (24 * 60)) + "일 " + (over % (24 * 60) / 60) + "시간 전";
    }
    /** 특정 시각 기준 D-Day (예약 알림용) */
    public long getDDayAt(LocalDateTime time) {
        return Math.floorDiv(Duration.between(time, getExpiryDateTime()).toMinutes(), 24L * 60);
    }
    public boolean isUrgent() {
        return getDDay() <= 1;
    }
}