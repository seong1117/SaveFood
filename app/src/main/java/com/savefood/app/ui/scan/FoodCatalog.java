package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 품목별 기본값(아이콘, 보관 장소, 권장 보관 일수)과 이름으로 품목 찾기. 안드로이드 의존 없음(단위 테스트 가능).
 * 사진 분류·가격표·영수증 등록과 홈 화면 아이콘에서 함께 쓴다.
 *
 * 1) 품목마다 대표 낱말을 둔다. 이름에 그 낱말이 들어 있으면 그 품목이다
 *    (순두부·부침두부·찌개용두부 → 두부, 닭가슴살·닭다리 → 닭고기, 한우 등심·차돌박이 → 소고기).
 * 2) 낱말 표기: "배$" = 낱말 끝에서만("나주배"는 배, "배추"는 아님), "등심~" = 약한 낱말(부위처럼 여러 고기에 쓰여서
 *    "돼지", "닭" 같은 낱말이 같이 있으면 진다).
 * 3) 여러 낱말이 맞으면: 강한 낱말 → 뒤에 나온 낱말 → 긴 낱말 순서로 고른다(배추김치 → 김치, 우삼겹 → 소고기).
 * 4) 영수증·가격표는 품명 칸 길이에서 이름을 잘라 인쇄한다. 이름 끝이 아는 낱말의 앞부분이면 채운 후보를 낸다(completions).
 * TODO: 보관 일수는 임시값. 식약처 권장 기한으로 보정할 것.
 */
public final class FoodCatalog {

    public static final class Entry {
        /** 대표 이름(빠른 선택 칩, 사진 모델 라벨) */
        public final String name;
        /** 아이콘 이모지. 그림 아이콘(icon)이 있으면 그것을 먼저 쓰고, 이모지는 글자만 되는 곳(저장값 등)에 쓴다 */
        public final String emoji;
        /** 이모지가 없는 품목의 그림 아이콘 이름(drawable "ic_food_" + icon). 없으면 null */
        @Nullable public final String icon;
        public final String location;   // 냉장실 / 냉동실 / 실온
        public final int days;
        /** 스캔 화면 "자주 사는 품목" 칩에 보이는가 */
        final boolean quick;
        final Keyword[] keywords;

        Entry(boolean quick, String name, String emoji, @Nullable String icon, String location, int days,
              String... words) {
            this.quick = quick;
            this.name = name;
            this.emoji = emoji;
            this.icon = icon;
            this.location = location;
            this.days = days;
            this.keywords = new Keyword[words.length];
            for (int i = 0; i < words.length; i++) keywords[i] = new Keyword(words[i]);
        }
    }

    /** 이름에서 찾을 낱말 하나. */
    static final class Keyword {
        final String text;
        final boolean wordEnd;   // "$": 뒤에 한글이 이어지지 않을 때만
        final boolean weak;      // "~": 다른 강한 낱말이 있으면 진다

        Keyword(String raw) {
            String t = raw;
            boolean end = false, w = false;
            if (t.endsWith("$")) { end = true; t = t.substring(0, t.length() - 1); }
            if (t.endsWith("~")) { w = true; t = t.substring(0, t.length() - 1); }
            text = t;
            wordEnd = end;
            weak = w;
        }
    }

    /** 잘린 이름을 사전 낱말로 채운 후보. */
    public static final class Completion {
        /** 채운 이름(갓잡은싱싱한고 → 갓잡은싱싱한고등어) */
        public final String name;
        /** 채운 낱말(고등어) */
        public final String word;
        /** 두 글자 이상 맞고 후보가 하나뿐이라 바로 써도 되는가 */
        public final boolean sure;

        Completion(String name, String word, boolean sure) {
            this.name = name;
            this.word = word;
            this.sure = sure;
        }

        @NonNull
        @Override
        public String toString() { return name; }
    }

    private static final String FRIDGE = "냉장실", FREEZER = "냉동실", ROOM = "실온";

    // 앞의 16개는 빠른 선택 칩 순서이자 사진 모델의 라벨 이름이다(food_labels.txt). 이름을 바꾸지 않는다.
    // 같은 점수면 앞에 있는 품목이 이긴다.
    private static final Entry[] ENTRIES = {
            new Entry(true, "사과", "🍎", null, FRIDGE, 21, "사과", "부사", "홍로", "아오리"),
            new Entry(true, "양파", "🧅", null, ROOM, 30, "양파"),
            new Entry(true, "수박", "🍉", null, FRIDGE, 5, "수박"),
            new Entry(true, "감자", "🥔", null, ROOM, 21, "감자"),
            new Entry(true, "당근", "🥕", null, FRIDGE, 14, "당근"),
            new Entry(true, "대파", "🥬", null, FRIDGE, 7, "대파", "쪽파", "실파", "파$"),
            new Entry(true, "배추", "🥬", null, FRIDGE, 14, "배추", "알배기"),
            new Entry(true, "양배추", "🥬", null, FRIDGE, 14, "양배추", "적채"),
            new Entry(true, "토마토", "🍅", null, FRIDGE, 7, "토마토"),
            new Entry(true, "바나나", "🍌", null, ROOM, 5, "바나나"),
            new Entry(true, "오이", "🥒", null, FRIDGE, 7, "오이"),
            new Entry(true, "마늘", "🧄", null, FRIDGE, 30, "마늘"),
            new Entry(true, "귤", "🍊", null, ROOM, 10, "귤", "한라봉", "천혜향", "레드향", "황금향", "오렌지"),
            new Entry(true, "계란", "🥚", null, FRIDGE, 21, "계란", "달걀", "메추리알", "란$"),
            new Entry(true, "우유", "🥛", null, FRIDGE, 7, "우유", "두유", "요거트", "요구르트", "요플레", "생크림"),
            // 두부는 이모지가 없어 그림 아이콘을 쓴다. 이모지는 글자만 되는 곳에서 쓰는 대신(콩)
            new Entry(true, "두부", "🫘", "tofu", FRIDGE, 5, "두부", "유부"),

            // ---- 고기: 부위 이름은 약한 낱말("돼지등심"은 돼지, "등심"만 있으면 소) ----
            new Entry(false, "돼지고기", "🐷", null, FRIDGE, 3,
                    "돼지", "돈육", "한돈", "삼겹", "오겹", "목살", "앞다리", "뒷다리", "항정", "갈매기살", "가브리살",
                    "등갈비", "돈까스", "돈가스", "돈카츠", "족발", "보쌈", "제육", "대패", "수육~"),
            new Entry(false, "소고기", "🐮", null, FRIDGE, 3,
                    "소고기", "쇠고기", "한우", "육우", "우삼겹", "차돌", "채끝", "양지", "사태", "우둔", "홍두깨",
                    "부채살", "살치살", "토시살", "치마살", "업진", "척아이", "척롤", "꽃등심", "소갈비", "소불고기",
                    "떡갈비", "사골", "우족", "육회", "LA갈비",
                    "등심~", "안심~", "갈비~", "갈비살~", "불고기~", "국거리~", "장조림~", "스테이크~"),
            new Entry(false, "닭고기", "🐔", null, FRIDGE, 2, "닭", "치킨", "계육", "영계", "삼계", "백숙", "북채", "윙~"),
            new Entry(false, "오리고기", "🦆", null, FRIDGE, 3, "오리$", "오리고기", "훈제오리", "오리훈제", "오리주물럭"),
            new Entry(false, "양고기", "🐑", null, FRIDGE, 3, "양고기", "양갈비", "양꼬치", "램~"),

            // ---- 수산물 ----
            new Entry(false, "생선", "🐟", null, FRIDGE, 2,
                    "생선", "고등어", "갈치", "삼치", "꽁치", "조기", "굴비", "동태", "명태", "코다리", "황태", "북어",
                    "노가리", "연어", "참치", "광어", "우럭", "도미", "가자미", "장어", "민어", "방어", "아귀",
                    "임연수", "전어", "멸치", "숭어", "메로", "볼락", "명란", "대구~"),
            new Entry(false, "새우", "🦐", null, FREEZER, 30, "새우", "대하", "흰다리", "쉬림프"),
            new Entry(false, "오징어", "🦑", null, FRIDGE, 2, "오징어", "한치", "꼴뚜기"),
            new Entry(false, "문어", "🐙", null, FRIDGE, 2, "문어", "낙지", "주꾸미", "쭈꾸미"),
            new Entry(false, "조개", "🦪", null, FRIDGE, 2,
                    "조개", "바지락", "홍합", "꼬막", "가리비", "전복", "재첩", "소라", "골뱅이", "석화", "멍게",
                    "해삼", "굴$", "백합~"),
            new Entry(false, "게", "🦀", null, FRIDGE, 2, "꽃게", "대게", "홍게", "킹크랩", "크랩", "게살", "게장", "맛살", "게$"),
            new Entry(false, "해조류", "🌿", null, ROOM, 90, "미역", "다시마", "파래", "매생이", "감태", "꼬시래기", "김$", "톳$"),

            // ---- 채소 ----
            new Entry(false, "김치", "🥬", null, FRIDGE, 30,
                    "김치", "깍두기", "겉절이", "섞박지", "동치미", "배추김치", "열무김치", "총각김치", "포기김치"),
            new Entry(false, "버섯", "🍄", null, FRIDGE, 5, "버섯", "표고", "느타리", "팽이", "새송이", "양송이", "목이", "만가닥", "송이~"),
            new Entry(false, "콩나물", "🌱", null, FRIDGE, 3, "콩나물", "숙주"),
            new Entry(false, "잎채소", "🥬", null, FRIDGE, 5,
                    "상추", "깻잎", "시금치", "청경채", "케일", "양상추", "로메인", "부추", "쌈채소", "미나리", "쑥갓",
                    "근대", "아욱", "열무", "얼갈이"),
            new Entry(false, "브로콜리", "🥦", null, FRIDGE, 5, "브로콜리", "콜리플라워"),
            new Entry(false, "고추", "🌶️", null, FRIDGE, 10, "고추", "청양"),
            new Entry(false, "파프리카", "🫑", null, FRIDGE, 10, "파프리카", "피망"),
            new Entry(false, "가지", "🍆", null, FRIDGE, 5, "가지$"),
            new Entry(false, "고구마", "🍠", null, ROOM, 14, "고구마", "호박고구마", "밤고구마"),
            new Entry(false, "애호박", "🥒", null, FRIDGE, 7, "애호박", "주키니"),
            new Entry(false, "단호박", "🎃", null, ROOM, 14, "단호박", "늙은호박", "호박~"),
            new Entry(false, "옥수수", "🌽", null, FRIDGE, 3, "옥수수", "콘~"),

            // ---- 과일 ----
            new Entry(false, "딸기", "🍓", null, FRIDGE, 3, "딸기"),
            new Entry(false, "포도", "🍇", null, FRIDGE, 7, "포도", "샤인머스캣", "머스캣", "거봉"),
            new Entry(false, "복숭아", "🍑", null, ROOM, 3, "복숭아", "천도", "황도"),
            new Entry(false, "참외", "🍈", null, FRIDGE, 5, "참외", "멜론"),
            new Entry(false, "키위", "🥝", null, FRIDGE, 14, "키위"),
            new Entry(false, "레몬", "🍋", null, FRIDGE, 21, "레몬", "라임"),
            new Entry(false, "배", "🍐", null, FRIDGE, 21, "배$", "서양배"),
            new Entry(false, "체리", "🍒", null, FRIDGE, 5, "체리"),
            new Entry(false, "블루베리", "🫐", null, FRIDGE, 7, "블루베리"),
            new Entry(false, "파인애플", "🍍", null, FRIDGE, 5, "파인애플"),
            new Entry(false, "망고", "🥭", null, ROOM, 5, "망고"),
            new Entry(false, "아보카도", "🥑", null, ROOM, 5, "아보카도"),

            // ---- 유제품·가공품·곡류 ----
            new Entry(false, "치즈", "🧀", null, FRIDGE, 21, "치즈"),
            new Entry(false, "버터", "🧈", null, FRIDGE, 60, "버터"),
            new Entry(false, "햄", "🥓", null, FRIDGE, 14, "스팸", "리챔", "런천", "베이컨", "햄$"),
            new Entry(false, "소시지", "🌭", null, FRIDGE, 14, "소시지", "비엔나", "프랑크", "후랑크", "핫도그"),
            new Entry(false, "어묵", "🍢", null, FRIDGE, 7, "어묵", "오뎅"),
            new Entry(false, "만두", "🥟", null, FREEZER, 60, "만두", "교자", "딤섬"),
            new Entry(false, "떡", "🍡", null, FRIDGE, 3, "떡"),
            new Entry(false, "빵", "🍞", null, ROOM, 3, "빵", "베이글", "바게트", "크루아상", "토스트"),
            new Entry(false, "쌀", "🍚", null, ROOM, 180, "쌀", "현미", "잡곡", "오곡", "즉석밥", "햇반"),
            new Entry(false, "면", "🍜", null, ROOM, 180,
                    "라면", "국수", "소면", "우동", "냉면", "쫄면", "칼국수", "쌀국수", "당면", "메밀", "짬뽕", "짜장"),
            new Entry(false, "파스타", "🍝", null, ROOM, 180, "파스타", "스파게티", "마카로니", "펜네"),
            new Entry(false, "시리얼", "🥣", null, ROOM, 90, "시리얼", "콘플레이크", "그래놀라", "오트밀"),
            new Entry(false, "콩", "🫘", null, ROOM, 180, "콩", "서리태", "완두", "팥"),
            new Entry(false, "견과", "🥜", null, ROOM, 90,
                    "견과", "땅콩", "아몬드", "호두", "캐슈", "피스타치오", "마카다미아", "잣$", "밤$"),

            // ---- 양념·음료 ----
            new Entry(false, "양념", "🧂", null, FRIDGE, 90,
                    "간장", "된장", "고추장", "쌈장", "춘장", "식초", "소스", "굴소스", "케첩", "케찹", "마요네즈",
                    "머스타드", "액젓", "새우젓", "젓갈", "소금", "설탕", "후추", "고춧가루", "참기름", "들기름",
                    "식용유", "올리브유", "카놀라유", "포도씨유", "드레싱", "다시다", "맛술", "미림"),
            new Entry(false, "꿀", "🍯", null, ROOM, 365, "꿀", "물엿", "올리고당", "조청"),
            new Entry(false, "카레", "🍛", null, ROOM, 180, "카레", "커리"),
            new Entry(false, "주스", "🧃", null, FRIDGE, 7, "주스", "쥬스", "에이드"),
            new Entry(false, "탄산음료", "🥤", null, ROOM, 180, "콜라", "사이다", "탄산"),
            new Entry(false, "커피", "☕", null, ROOM, 180, "커피", "원두", "아메리카노", "라떼"),
            new Entry(false, "맥주", "🍺", null, ROOM, 180, "맥주"),
            new Entry(false, "소주·막걸리", "🍶", null, ROOM, 365, "소주", "막걸리", "청주", "사케"),
            new Entry(false, "와인", "🍷", null, ROOM, 365, "와인"),
            new Entry(false, "생수", "💧", null, ROOM, 365, "생수"),
    };

    public static final String DEFAULT_EMOJI = "🛒";
    public static final int DEFAULT_DAYS = 7;

    /** 잘린 끝을 볼 때 최대 몇 글자까지 보는가 */
    private static final int MAX_TAIL = 4;
    /** 잘린 이름 후보 최대 개수 */
    private static final int MAX_COMPLETIONS = 3;

    private FoodCatalog() { }

    /** 빠른 선택 칩에 보일 품목(칩 순서). */
    @NonNull
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) if (e.quick) out.add(e);
        return Collections.unmodifiableList(out);
    }

    /**
     * 이름에 맞는 품목. 모르면 null.
     * 강한 낱말 → 뒤에 나온 낱말(한국어 합성어는 뒤가 중심: 배추김치, 우유식빵) → 긴 낱말(양배추 > 배추) 순으로 고른다.
     */
    @Nullable
    public static Entry find(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        Entry best = null;
        boolean bestStrong = false;
        int bestEnd = -1, bestLen = 0;
        for (Entry e : ENTRIES) {
            for (Keyword k : e.keywords) {
                int end = lastMatchEnd(name, k);
                if (end < 0) continue;
                boolean strong = !k.weak;
                int len = k.text.length();
                boolean better = best == null
                        || (strong != bestStrong ? strong : end != bestEnd ? end > bestEnd : len > bestLen);
                if (better) {
                    best = e;
                    bestStrong = strong;
                    bestEnd = end;
                    bestLen = len;
                }
            }
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

    // ---------------- 잘린 이름 채우기 ----------------

    /**
     * 끝이 잘린 이름("갓잡은싱싱한고")을 사전 낱말로 채운 후보(가능성 높은 순, 최대 3개).
     * 이름 끝 1~4글자가 아는 낱말의 앞부분일 때만 낸다. 이미 아는 낱말로 끝나면 잘리지 않은 것으로 본다.
     * 끝 한 글자만 맞는 경우는 오탐이 많아서, 아는 낱말이 없거나 그 글자가 아는 낱말 바로 뒤에 붙어 있을 때만 본다
     * ("국산콩두" → 두부·두유, "서울우유 저지방" → 없음).
     */
    @NonNull
    public static List<Completion> completions(@Nullable String name) {
        List<Completion> out = new ArrayList<>();
        if (name == null) return out;
        String s = name.trim();
        if (s.isEmpty() || !isHangul(s.charAt(s.length() - 1)) || endsWithKeyword(s)) return out;

        int known = lastKeywordEnd(s);
        int run = 0;
        while (run < s.length() && isHangul(s.charAt(s.length() - 1 - run))) run++;
        for (int k = Math.min(MAX_TAIL, run); k >= 1; k--) {
            if (k == 1 && known >= 0 && known != s.length() - 1) break;
            String head = s.substring(0, s.length() - k), tail = s.substring(s.length() - k);
            for (Entry e : ENTRIES) {
                for (Keyword w : e.keywords) {
                    if (w.text.length() <= k || !w.text.startsWith(tail) || !allHangul(w.text)) continue;
                    if (containsWord(out, w.text)) continue;
                    out.add(new Completion(head + w.text, w.text, false));
                    if (out.size() == MAX_COMPLETIONS) break;
                }
                if (out.size() == MAX_COMPLETIONS) break;
            }
            if (!out.isEmpty()) {
                if (k >= 2 && out.size() == 1) {
                    Completion only = out.get(0);
                    out.set(0, new Completion(only.name, only.word, true));
                }
                return out;
            }
        }
        return out;
    }

    /**
     * 품명 후보 목록에 잘린 이름을 채운 후보를 더한다(가격표·포장지 칩).
     * 확실한 후보는 원래 이름 앞에, 아니면 뒤에 둔다. 최대 max 개.
     */
    @NonNull
    public static List<String> withCompletions(@NonNull List<String> names, int max) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            List<Completion> fixes = completions(n);
            boolean sure = fixes.size() == 1 && fixes.get(0).sure;
            if (sure) addUnique(out, fixes.get(0).name);
            addUnique(out, n);
            if (!sure) for (Completion c : fixes) addUnique(out, c.name);
        }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // ---------------- 내부 ----------------

    /** 낱말이 마지막으로 맞는 끝 위치. 없으면 -1. "$" 낱말은 뒤에 한글이 이어지면 안 된다. */
    private static int lastMatchEnd(String name, Keyword k) {
        int found = -1;
        int at = name.indexOf(k.text);
        while (at >= 0) {
            int end = at + k.text.length();
            if (!k.wordEnd || end >= name.length() || !isHangul(name.charAt(end))) found = end;
            at = name.indexOf(k.text, at + 1);
        }
        return found;
    }

    /** 이름에서 아는 낱말이 끝나는 가장 뒤 위치. 없으면 -1. */
    private static int lastKeywordEnd(String s) {
        int last = -1;
        for (Entry e : ENTRIES) {
            for (Keyword k : e.keywords) last = Math.max(last, lastMatchEnd(s, k));
        }
        return last;
    }

    private static boolean endsWithKeyword(String s) {
        for (Entry e : ENTRIES) {
            for (Keyword k : e.keywords) if (s.endsWith(k.text)) return true;
        }
        return false;
    }

    private static boolean containsWord(List<Completion> list, String word) {
        for (Completion c : list) if (c.word.equals(word)) return true;
        return false;
    }

    private static void addUnique(List<String> out, String s) {
        if (s != null && !s.isEmpty() && !out.contains(s)) out.add(s);
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }

    private static boolean allHangul(String s) {
        for (int i = 0; i < s.length(); i++) if (!isHangul(s.charAt(i))) return false;
        return true;
    }
}
