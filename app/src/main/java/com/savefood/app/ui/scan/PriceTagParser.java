package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 진열대 가격표 OCR 결과 → 품명 후보. 안드로이드 의존 없음(단위 테스트 가능).
 *
 * 1) 가격(2,980원), 단위가격(100ml당), 행사 문구(2+1, [행사]), 상품 번호를 지운다.
 * 2) 원산지·행사기간처럼 품명이 아닌 줄은 버리고, 한글이 2자 이상 남은 줄만 후보로 본다.
 * 3) 가격표의 품명은 크게 인쇄되므로 글자 높이가 큰 순서(같으면 위쪽 먼저)로 최대 3개를 돌려준다.
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
    private static final Pattern EDGE_JUNK = Pattern.compile("^[\\s\\-.,:/*·•~]+|[\\s\\-.,:/*·•~]+$");

    private PriceTagParser() { }

    /** 품명 후보(가능성 높은 순, 최대 3개). */
    @NonNull
    public static List<String> names(@NonNull List<OcrPiece> pieces) {
        List<OcrPiece> sorted = new ArrayList<>(pieces);
        // 큰 글자 먼저, 높이가 비슷하면(10% 이내) 위쪽 먼저
        sorted.sort((a, b) -> {
            int ha = a.height(), hb = b.height();
            if (Math.abs(ha - hb) > Math.max(ha, hb) * 0.1f) return Integer.compare(hb, ha);
            return Integer.compare(a.top, b.top);
        });

        List<String> out = new ArrayList<>();
        for (OcrPiece p : sorted) {
            String name = clean(p.text);
            if (name != null && !out.contains(name)) out.add(name);
            if (out.size() == 3) break;
        }
        return out;
    }

    /** 한 줄에서 가격·행사·번호를 지운 품명. 품명이 아니면 null. */
    static String clean(@NonNull String line) {
        String s = BRACKET.matcher(line).replaceAll(" ");
        s = UNIT_PRICE.matcher(s).replaceAll(" ");
        s = LONG_CODE.matcher(s).replaceAll(" ");
        s = PRICE.matcher(s).replaceAll(" ");
        s = PROMO.matcher(s).replaceAll(" ");
        for (String w : SKIP_WORDS) {
            if (s.contains(w)) return null;
        }
        s = s.replaceAll("\\s{2,}", " ");
        s = EDGE_JUNK.matcher(s).replaceAll("");
        int hangul = 0;
        for (int i = 0; i < s.length(); i++) {
            if (HANGUL.matcher(String.valueOf(s.charAt(i))).matches()) hangul++;
        }
        return hangul >= 2 ? s : null;
    }
}
