package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 품목별 기본값(이모지, 보관 장소, 권장 보관 일수). 사진 분류·영수증 등록에서 함께 쓴다.
 * TODO: 보관 일수는 임시값. 식약처 권장 기한으로 보정할 것.
 */
public final class FoodCatalog {

    public static final class Entry {
        public final String name;
        public final String emoji;
        public final String location;   // 냉장실 / 냉동실 / 실온
        public final int days;

        Entry(String name, String emoji, String location, int days) {
            this.name = name;
            this.emoji = emoji;
            this.location = location;
            this.days = days;
        }
    }

    // 사진 모델의 라벨 이름과 같게 쓴다(food_labels.txt)
    private static final Entry[] ENTRIES = {
            new Entry("사과", "🍎", "냉장실", 21),
            new Entry("양파", "🧅", "실온", 30),
            new Entry("수박", "🍉", "냉장실", 5),
            new Entry("감자", "🥔", "실온", 21),
            new Entry("당근", "🥕", "냉장실", 14),
            new Entry("대파", "🥬", "냉장실", 7),
            new Entry("배추", "🥬", "냉장실", 14),
            new Entry("양배추", "🥬", "냉장실", 14),
            new Entry("토마토", "🍅", "냉장실", 7),
            new Entry("바나나", "🍌", "실온", 5),
            new Entry("오이", "🥒", "냉장실", 7),
            new Entry("마늘", "🧄", "냉장실", 30),
            new Entry("귤", "🍊", "실온", 10),
            new Entry("계란", "🥚", "냉장실", 21),
            new Entry("우유", "🥛", "냉장실", 7),
            new Entry("두부", "🧈", "냉장실", 5),
    };

    public static final String DEFAULT_EMOJI = "🛒";
    public static final int DEFAULT_DAYS = 7;

    private FoodCatalog() { }

    /** 전체 품목(빠른 선택 칩 순서). */
    @NonNull
    public static List<Entry> all() {
        return Collections.unmodifiableList(Arrays.asList(ENTRIES));
    }

    /** 이름이 품목명을 포함하면 그 품목(긴 이름 우선: "양배추"가 "배추"보다 먼저). */
    @Nullable
    public static Entry find(@Nullable String name) {
        if (name == null) return null;
        Entry best = null;
        for (Entry e : ENTRIES) {
            if (name.contains(e.name) && (best == null || e.name.length() > best.name.length())) best = e;
        }
        return best;
    }

    @NonNull
    public static String emojiFor(@Nullable String name) {
        Entry e = find(name);
        return e == null ? DEFAULT_EMOJI : e.emoji;
    }

    public static int daysFor(@Nullable String name) {
        Entry e = find(name);
        return e == null ? DEFAULT_DAYS : e.days;
    }
}
