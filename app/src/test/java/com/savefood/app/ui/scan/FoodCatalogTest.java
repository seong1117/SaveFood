package com.savefood.app.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FoodCatalogTest {

    private static String kind(String name) {
        FoodCatalog.Entry e = FoodCatalog.find(name);
        return e == null ? null : e.name;
    }

    private static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        for (FoodCatalog.Completion c : FoodCatalog.completions(name)) out.add(c.word);
        return out;
    }

    @Test
    public void tofuKindsShareTofuIcon() {
        for (String n : new String[] { "순두부", "부침두부", "찌개용두부", "국산콩 두부 300g", "연두부" }) {
            assertEquals(n, "두부", kind(n));
        }
        assertEquals("tofu", FoodCatalog.find("순두부").icon);
    }

    @Test
    public void chickenAnyCut() {
        for (String n : new String[] { "닭가슴살", "닭다리", "닭봉", "생닭 1kg", "닭안심", "닭갈비", "치킨너겟" }) {
            assertEquals(n, "닭고기", kind(n));
        }
        assertEquals("🐔", FoodCatalog.emojiFor("닭다리살"));
    }

    @Test
    public void beefAnyCut() {
        for (String n : new String[] { "한우 등심", "소고기 국거리", "차돌박이", "우삼겹", "등심", "LA갈비", "호주산 척아이롤" }) {
            assertEquals(n, "소고기", kind(n));
        }
        assertEquals("🐮", FoodCatalog.emojiFor("한우 채끝"));
    }

    @Test
    public void speciesWordBeatsSharedCutName() {
        assertEquals("돼지고기", kind("돼지등심"));
        assertEquals("돼지고기", kind("한돈 목살"));
        assertEquals("돼지고기", kind("삼겹살"));
        assertEquals("돼지고기", kind("돈까스용 안심"));
        assertEquals("돼지고기", kind("양념돼지갈비"));
        assertEquals("양고기", kind("양갈비"));
    }

    @Test
    public void lookAlikeWordsAreNotMeat() {
        assertEquals("양념", kind("소금"));
        assertEquals("양념", kind("굴소스"));
        assertEquals("소시지", kind("비엔나 소시지"));
        assertFalse("오리고기".equals(kind("오리지널 감자칩")));
        assertEquals("계란", kind("계란"));
        assertEquals("생선", kind("명란"));
    }

    @Test
    public void headWordOfCompoundWins() {
        assertEquals("김치", kind("배추김치"));
        assertEquals("김치", kind("열무김치"));
        assertEquals("양배추", kind("양배추"));
        assertEquals("양파", kind("양파"));
        assertEquals("고구마", kind("호박고구마"));
        assertEquals("우유", kind("검은콩두유"));
        assertEquals("양념", kind("고추장"));
        assertEquals("고추", kind("청양고추"));
        assertEquals("콩나물", kind("무농약 콩나물"));
    }

    @Test
    public void wordEndOnlyKeywords() {
        assertEquals("배", kind("나주배 7.5kg"));
        assertEquals("배추", kind("절임배추"));
        assertEquals("해조류", kind("조미김 20봉"));
        assertEquals("김치", kind("김치"));
        assertEquals("조개", kind("생굴 300g"));
        assertEquals("생선", kind("영광굴비"));
        assertEquals("대파", kind("대파 1단"));
    }

    @Test
    public void unknownFallsBackToCart() {
        assertNull(FoodCatalog.find("이름없는상품"));
        assertEquals(FoodCatalog.DEFAULT_EMOJI, FoodCatalog.emojiFor("이름없는상품"));
        assertEquals(FoodCatalog.DEFAULT_DAYS, FoodCatalog.daysFor(null));
    }

    @Test
    public void quickChipsStayTheSame() {
        List<String> names = new ArrayList<>();
        for (FoodCatalog.Entry e : FoodCatalog.all()) names.add(e.name);
        assertEquals(Arrays.asList("사과", "양파", "수박", "감자", "당근", "대파", "배추", "양배추", "토마토",
                "바나나", "오이", "마늘", "귤", "계란", "우유", "두부"), names);
    }

    // ---------------- 잘린 이름 ----------------

    @Test
    public void cutAfterOneSyllableGivesChoices() {
        List<FoodCatalog.Completion> c = FoodCatalog.completions("갓잡은싱싱한고");
        assertTrue(words("갓잡은싱싱한고").contains("고등어"));
        assertEquals("갓잡은싱싱한고등어", c.get(0).name);
        assertFalse(c.get(0).sure);
        assertTrue(c.size() <= 3);
    }

    @Test
    public void cutAfterTwoSyllablesIsSure() {
        List<FoodCatalog.Completion> c = FoodCatalog.completions("갓잡은싱싱한고등");
        assertEquals(1, c.size());
        assertEquals("갓잡은싱싱한고등어", c.get(0).name);
        assertTrue(c.get(0).sure);
        assertEquals("국내산돼지앞다리", FoodCatalog.completions("국내산돼지앞다").get(0).name);
    }

    @Test
    public void oneSyllableRightAfterKnownWord() {
        assertTrue(words("국산콩두").contains("두부"));
        assertTrue(words("양념돼지갈").contains("갈비"));
    }

    @Test
    public void wholeNamesAreLeftAlone() {
        assertTrue(FoodCatalog.completions("갓잡은싱싱한고등어").isEmpty());
        assertTrue(FoodCatalog.completions("청양고추").isEmpty());
        assertTrue(FoodCatalog.completions("국산콩").isEmpty());
        assertTrue(FoodCatalog.completions("서울우유 1L").isEmpty());
        // 아는 품목이 이미 있으면 끝 한 글자로는 채우지 않는다(저지방 → 방울토마토 같은 오탐)
        assertTrue(FoodCatalog.completions("서울우유 저지방").isEmpty());
    }

    @Test
    public void candidateChipsPutSureFixFirst() {
        assertEquals(Arrays.asList("갓잡은싱싱한고등어", "갓잡은싱싱한고등"),
                FoodCatalog.withCompletions(Arrays.asList("갓잡은싱싱한고등"), 5));
        List<String> unsure = FoodCatalog.withCompletions(Arrays.asList("갓잡은싱싱한고"), 5);
        assertEquals("갓잡은싱싱한고", unsure.get(0));
        assertTrue(unsure.contains("갓잡은싱싱한고등어"));
        assertEquals(2, FoodCatalog.withCompletions(Arrays.asList("갓잡은싱싱한고", "고등어"), 2).size());
    }
}
