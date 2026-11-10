package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 진열대 가격표 OCR 결과 → 품명 후보. 안드로이드 의존 없음(단위 테스트 가능).
 *
 * 1) 가격(2,980원), 단위가격(100ml당), 행사 문구(2+1, [행사]), 상품 번호를 지운다.
 * 2) 원산지·행사기간처럼 품명이 아닌 줄은 버리고, 한글이 2자 이상 남은 줄만 후보로 본다.
 * 3) OCR 이 붙인 기호(' ^ " | 등)와 낱자모를 지우고, 같은 낱말이 되풀이되면 하나만 남긴다.
 * 4) 긴 품명은 OCR 이 두 줄이나 두 칸으로 나눠 읽는다. 글자 크기가 비슷하고 붙어 있는 조각은 한 품명으로 잇는다.
 * 5) 가격표의 품명은 크게 인쇄되므로 글자 높이가 큰 순서(같으면 위쪽 먼저)로 최대 3개를 돌려준다.
 *    이은 품명이 틀릴 수 있으니, 이은 묶음 안에서 가장 큰 한 줄도 후보로 남긴다.
 */
public final class PriceTagParser {

    // 이 말이 남아 있으면 품명 줄이 아니다
    private static final String[] SKIP_WORDS = {
            "행사", "할인", "특가", "세일", "SALE", "기간", "정상가", "판매가", "회원", "카드", "쿠폰",
            "원산지", "단위가격", "가격", "이벤트", "증정", "제조", "판매원", "유통", "보관", "까지"
    };

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern BRACKET = Pattern.compile("[\\[<【(][^\\]>】)]*[\\]>】)]");
    private static final Pattern PROMO = Pattern.compile("\\d\\s*\\+\\s*\\d");
    private static final Pattern UNIT_PRICE = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:ml|mL|ML|g|kg|L|l|개|입|봉)\\s*당.*");
    private static final Pattern PRICE = Pattern.compile("\\d{1,3}(?:,\\d{3})+\\s*원?|\\d+\\s*원");
    private static final Pattern LONG_CODE = Pattern.compile("\\d{8,14}");
    // 품명에 남겨 둘 글자: 한글 음절, 영문, 숫자, 공백, 용량·묶음 표기에 쓰는 몇 가지 기호. 나머지(' ^ " | ~ 낱자모 등)는 OCR 잡음
    private static final Pattern NOISE = Pattern.compile("[^가-힣A-Za-z0-9\\s.,/&%×\\-]");
    private static final Pattern EDGE_PUNCT = Pattern.compile("^[.,/&%×\\-]+|[.,/&%×\\-]+$");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)?");

    // 조각을 한 품명으로 이을 때의 기준(글자 높이에 대한 비율)
    private static final float SAME_SIZE = 0.6f;     // 묶음의 가장 큰 글자 대비 이만큼 이상이면 같은 크기
    private static final float LINE_GAP = 0.8f;      // 위아래 줄 사이 간격
    private static final float WORD_GAP = 1.5f;      // 같은 줄 좌우 간격

    private PriceTagParser() { }

    /** 품명 조각 하나(정리된 글자 + 위치). */
    private static final class Part {
        final String text;
        final OcrPiece piece;

        Part(String text, OcrPiece piece) {
            this.text = text;
            this.piece = piece;
        }
    }

    /** 이어 붙인 품명 묶음. */
    private static final class Group {
        final List<Part> parts = new ArrayList<>();
        int maxHeight, top;

        void add(Part p) {
            if (parts.isEmpty() || p.piece.height() > maxHeight) maxHeight = p.piece.height();
            top = parts.isEmpty() ? p.piece.top : Math.min(top, p.piece.top);
            parts.add(p);
        }

        /** 위에서 아래, 같은 줄은 왼쪽부터 이은 글자(되풀이 낱말 제거). */
        String text() {
            List<Part> sorted = new ArrayList<>(parts);
            sorted.sort((a, b) -> {
                boolean sameRow = Math.abs(a.piece.centerY() - b.piece.centerY())
                        < Math.min(a.piece.height(), b.piece.height()) * 0.5f;
                if (sameRow) return Integer.compare(a.piece.left, b.piece.left);
                return Float.compare(a.piece.centerY(), b.piece.centerY());
            });
            StringBuilder sb = new StringBuilder();
            for (Part p : sorted) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p.text);
            }
            return dedupe(sb.toString());
        }

        /** 묶음 안에서 가장 큰 한 줄. 크기가 비슷하면(10% 이내) 한글이 많은 쪽(지역명보다 품명). */
        String biggest() {
            Part best = parts.get(0);
            for (Part p : parts) {
                int h = p.piece.height(), bh = best.piece.height();
                boolean similar = Math.abs(h - bh) <= Math.max(h, bh) * 0.1f;
                if ((!similar && h > bh) || (similar && hangulCount(p.text) > hangulCount(best.text))) best = p;
            }
            return best.text;
        }
    }

    /** 품명 후보(가능성 높은 순, 최대 3개). */
    @NonNull
    public static List<String> names(@NonNull List<OcrPiece> pieces) {
        // 1) 줄마다 정리하고 품명이 아닌 줄은 버린다
        List<Part> parts = new ArrayList<>();
        for (OcrPiece p : pieces) {
            String name = clean(p.text);
            if (name != null) parts.add(new Part(name, p));
        }
        // 2) 큰 글자 먼저, 높이가 비슷하면(10% 이내) 위쪽 먼저
        parts.sort((a, b) -> {
            int ha = a.piece.height(), hb = b.piece.height();
            if (Math.abs(ha - hb) > Math.max(ha, hb) * 0.1f) return Integer.compare(hb, ha);
            return Integer.compare(a.piece.top, b.piece.top);
        });
        // 3) 큰 조각부터 묶음을 만들고, 크기가 비슷하고 붙어 있는 조각을 끌어온다
        List<Group> groups = new ArrayList<>();
        boolean[] used = new boolean[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            if (used[i]) continue;
            Group g = new Group();
            g.add(parts.get(i));
            used[i] = true;
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = 0; j < parts.size(); j++) {
                    if (used[j] || !touches(g, parts.get(j))) continue;
                    g.add(parts.get(j));
                    used[j] = true;
                    grew = true;
                }
            }
            groups.add(g);
        }
        // 4) 묶음 순서 = 가장 큰 글자 순(같으면 위쪽). 묶음 글자, 그다음 묶음의 가장 큰 줄
        groups.sort((a, b) -> {
            if (Math.abs(a.maxHeight - b.maxHeight) > Math.max(a.maxHeight, b.maxHeight) * 0.1f) {
                return Integer.compare(b.maxHeight, a.maxHeight);
            }
            return Integer.compare(a.top, b.top);
        });
        List<String> out = new ArrayList<>();
        for (Group g : groups) {
            addCandidate(out, g.text());
            if (g.parts.size() > 1) addCandidate(out, g.biggest());
            if (out.size() >= 3) break;
        }
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    private static void addCandidate(List<String> out, @Nullable String name) {
        if (name != null && !name.isEmpty() && !out.contains(name)) out.add(name);
    }

    /** 조각 p 가 묶음 g 의 어느 조각과 붙어 있고, 글자 크기가 비슷한가. */
    private static boolean touches(Group g, Part p) {
        OcrPiece b = p.piece;
        if (b.height() < g.maxHeight * SAME_SIZE || g.maxHeight < b.height() * SAME_SIZE) return false;
        for (Part q : g.parts) {
            OcrPiece a = q.piece;
            int h = Math.max(a.height(), b.height());
            boolean overlapX = a.left < b.right && b.left < a.right;
            int gapY = Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom);   // 음수면 겹침
            int gapX = Math.max(a.left, b.left) - Math.min(a.right, b.right);
            boolean sameRow = Math.abs(a.centerY() - b.centerY()) < h * 0.5f;
            if (overlapX && gapY <= h * LINE_GAP) return true;          // 위아래로 이어진 줄
            if (sameRow && gapX <= h * WORD_GAP) return true;            // 같은 줄의 옆 칸
        }
        return false;
    }

    /** 한 줄에서 가격·행사·번호·잡음 기호를 지운 품명. 품명이 아니면 null. */
    @Nullable
    static String clean(@NonNull String line) {
        String s = BRACKET.matcher(line).replaceAll(" ");
        s = UNIT_PRICE.matcher(s).replaceAll(" ");
        s = LONG_CODE.matcher(s).replaceAll(" ");
        s = PRICE.matcher(s).replaceAll(" ");
        s = PROMO.matcher(s).replaceAll(" ");
        for (String w : SKIP_WORDS) {
            if (s.contains(w)) return null;
        }
        s = NOISE.matcher(s).replaceAll(" ");
        s = tidyTokens(s);
        return hangulCount(s) >= 2 ? dedupe(s) : null;
    }

    private static int hangulCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '가' && c <= '힣') n++;
        }
        return n;
    }

    /** 낱말 앞뒤 기호를 떼고, 숫자 뒤가 아닌 외톨이 영문 한 글자(I, l 같은 OCR 잡음)를 버린다. */
    @NonNull
    private static String tidyTokens(@NonNull String s) {
        StringBuilder sb = new StringBuilder();
        String prev = "";
        for (String raw : s.trim().split("\\s+")) {
            String t = EDGE_PUNCT.matcher(raw).replaceAll("");
            if (t.isEmpty()) continue;
            boolean loneLatin = t.length() == 1 && Character.isLetter(t.charAt(0)) && !HANGUL.matcher(t).matches();
            if (loneLatin && !NUMBER_TOKEN.matcher(prev).matches()) continue;   // "1 L" 의 L 은 남긴다
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
            prev = t;
        }
        return sb.toString();
    }

    /** 같은 낱말이 다시 나오거나, 앞 낱말의 끝부분과 같으면 지운다("미역 미역", "완도미역 미역" → 앞의 것만). */
    @NonNull
    static String dedupe(@NonNull String s) {
        List<String> kept = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (t.isEmpty()) continue;
            boolean repeat = false;
            for (String k : kept) {
                if (k.equals(t) || (t.length() >= 2 && k.length() > t.length() && k.endsWith(t)
                        && HANGUL.matcher(t).find())) {
                    repeat = true;
                    break;
                }
            }
            if (!repeat) kept.add(t);
        }
        return String.join(" ", kept);
    }
}
