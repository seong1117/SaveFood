package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 영수증 OCR 결과 → 품목(품명·수량) 목록 + 구매 날짜. 안드로이드 의존 없음(단위 테스트 가능).
 *
 * 1) OCR 은 품명 칸과 가격 칸을 따로 읽으므로, 세로 위치가 겹치는 조각을 한 줄로 다시 묶는다.
 * 2) 합계·카드·사업자 정보 같은 줄은 버리고, 한글 품명 + 숫자가 있는 줄만 품목으로 본다.
 * 3) 숫자 중 "단가 × 수량 = 금액"이 맞는 작은 정수를 수량으로 고른다(없으면 1). 가격은 쓰지 않는다.
 */
public final class ReceiptParser {

    public static final class Item {
        public final String name;
        public final int quantity;

        public Item(@NonNull String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @NonNull
        @Override
        public String toString() { return name + " × " + quantity; }
    }

    public static final class Result {
        public final List<Item> items;
        @Nullable public final LocalDate date;   // 구매 날짜(참고용), 못 찾으면 null

        Result(List<Item> items, @Nullable LocalDate date) {
            this.items = items;
            this.date = date;
        }
    }

    // 품목이 아닌 줄에 나오는 말
    private static final String[] SKIP_WORDS = {
            "합계", "총액", "총계", "소계", "부가세", "부가가치", "과세", "면세", "공급가", "봉사료",
            "카드", "승인", "할부", "결제", "현금", "거스름", "받을", "받은", "잔액", "포인트", "적립",
            "사업자", "대표", "전화", "주소", "TEL", "Tel", "tel", "영수증", "일시", "거래", "매출",
            "계산", "POS", "pos", "교환", "환불", "반품", "회원", "고객", "가맹", "상품명", "단가",
            "수량", "금액", "할인", "에누리", "봉투", "쿠폰", "감사", "www", "http"
    };

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    // 숫자 덩어리(쉼표 포함). 품명 안의 "1L", "500g", "4입" 처럼 단위가 붙은 것은 제외
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.,])\\d{1,3}(?:,\\d{3})+(?![\\w,가-힣])|(?<![\\w.,])\\d+(?![\\w.,가-힣])");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[\\s*#\\-.·•\\[\\]()0-9]*(?=[가-힣A-Za-z])");
    private static final Pattern COMMA_PRICE = Pattern.compile("\\d{1,3}(?:,\\d{3})+");
    private static final Pattern PRICE_ONLY_ROW = Pattern.compile("^[\\d,\\s*]+$");   // 숫자만 있는 줄(전화번호의 '-' 제외)
    private static final Pattern LONG_CODE = Pattern.compile("\\b\\d{8,14}\\b");   // 상품 바코드 번호

    private static final Pattern DATE_FULL =
            Pattern.compile("(20\\d{2})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
    private static final Pattern DATE_SHORT =
            Pattern.compile("(?<!\\d)(\\d{2})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})(?!\\d)");

    private ReceiptParser() { }

    @NonNull
    public static Result parse(@NonNull List<OcrPiece> pieces) {
        List<String> rows = toRows(pieces);
        return new Result(findItems(rows), findDate(rows));
    }

    /** 세로 중심이 가까운 조각끼리 한 줄로 묶고, 줄 안에서는 왼쪽부터 잇는다. */
    @NonNull
    static List<String> toRows(@NonNull List<OcrPiece> pieces) {
        List<OcrPiece> sorted = new ArrayList<>(pieces);
        sorted.sort(Comparator.comparingDouble(OcrPiece::centerY));

        List<List<OcrPiece>> rows = new ArrayList<>();
        for (OcrPiece p : sorted) {
            List<OcrPiece> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (last != null && Math.abs(avgCenter(last) - p.centerY()) < p.height() * 0.5f) {
                last.add(p);
            } else {
                List<OcrPiece> row = new ArrayList<>();
                row.add(p);
                rows.add(row);
            }
        }

        List<String> out = new ArrayList<>();
        for (List<OcrPiece> row : rows) {
            row.sort(Comparator.comparingInt(p -> p.left));
            StringBuilder sb = new StringBuilder();
            for (OcrPiece p : row) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p.text.trim());
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static float avgCenter(List<OcrPiece> row) {
        float sum = 0;
        for (OcrPiece p : row) sum += p.centerY();
        return sum / row.size();
    }

    @NonNull
    static List<Item> findItems(@NonNull List<String> rows) {
        List<Item> items = new ArrayList<>();
        String pendingName = null;   // 품명만 있고 숫자는 다음 줄에 있는 영수증 대비
        for (String raw : rows) {
            String row = LONG_CODE.matcher(raw).replaceAll(" ").trim();
            if (row.isEmpty() || isSkipRow(row) || findDateIn(row) != null) {
                pendingName = null;
                continue;
            }
            boolean hasHangul = HANGUL.matcher(row).find();
            List<Long> numbers = numbersAfterName(row);

            if (hasHangul) {
                String name = cleanName(row);
                if (name == null) continue;
                if (numbers.isEmpty()) {
                    pendingName = name;
                } else if (looksLikePrice(row, numbers)) {
                    items.add(new Item(name, guessQuantity(numbers)));
                    pendingName = null;
                }
            } else if (pendingName != null && PRICE_ONLY_ROW.matcher(row).matches()
                    && looksLikePrice(row, numbers)) {
                items.add(new Item(pendingName, guessQuantity(numbers)));
                pendingName = null;
            }
        }
        return items;
    }

    /** 가격처럼 보이는 숫자가 있는가: 쉼표 금액(1,000 이상) 또는 숫자 2개 이상에 마지막이 100 이상. 주소 번지 등 제외용. */
    private static boolean looksLikePrice(String row, List<Long> numbers) {
        if (COMMA_PRICE.matcher(row).find()) return true;
        return numbers.size() >= 2 && numbers.get(numbers.size() - 1) >= 100;
    }

    private static boolean isSkipRow(String row) {
        for (String w : SKIP_WORDS) {
            if (row.contains(w)) return true;
        }
        return false;
    }

    /** 품명 = 줄 앞쪽에서 첫 "떨어진 숫자" 전까지. 앞의 번호·기호는 지운다. */
    @Nullable
    static String cleanName(String row) {
        Matcher m = NUMBER.matcher(row);
        String head = row;
        while (m.find()) {
            // 품명보다 앞에 있는 순번(예: "01 서울우유")은 건너뛴다
            if (HANGUL.matcher(row.substring(0, m.start())).find()) {
                head = row.substring(0, m.start());
                break;
            }
        }
        head = LEADING_JUNK.matcher(head).replaceFirst("").trim();
        head = head.replaceAll("\\s{2,}", " ");
        return HANGUL.matcher(head).find() && head.length() >= 2 ? head : null;
    }

    /** 품명 뒤에 오는 숫자들(쉼표 제거). */
    static List<Long> numbersAfterName(String row) {
        List<Long> out = new ArrayList<>();
        Matcher h = HANGUL.matcher(row);
        int lastHangul = -1;
        while (h.find()) lastHangul = h.end();
        Matcher m = NUMBER.matcher(row);
        while (m.find()) {
            if (m.start() < lastHangul) continue;
            try {
                out.add(Long.parseLong(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** 단가 × 수량 = 금액 이 맞는 수량을 찾는다. 없으면 1~99 사이 첫 작은 수, 그것도 없으면 1. */
    static int guessQuantity(List<Long> n) {
        for (int i = 0; i + 2 < n.size(); i++) {
            long price = n.get(i), qty = n.get(i + 1), amount = n.get(i + 2);
            if (qty >= 1 && qty <= 99 && price * qty == amount) return (int) qty;
        }
        if (n.size() >= 2) {
            for (long v : n) {
                if (v >= 1 && v <= 99) return (int) v;
            }
        }
        return 1;
    }

    /** "거래일시/판매일" 줄의 날짜를 우선, 없으면 처음 나온 날짜. */
    @Nullable
    static LocalDate findDate(@NonNull List<String> rows) {
        LocalDate first = null;
        for (String row : rows) {
            LocalDate d = findDateIn(row);
            if (d == null) continue;
            if (row.contains("일시") || row.contains("판매") || row.contains("거래") || row.contains("구매")) {
                return d;
            }
            if (first == null) first = d;
        }
        return first;
    }

    @Nullable
    static LocalDate findDateIn(String row) {
        Matcher m = DATE_FULL.matcher(row);
        if (m.find()) return safeDate(m.group(1), m.group(2), m.group(3), 0);
        m = DATE_SHORT.matcher(row);
        if (m.find()) return safeDate(m.group(1), m.group(2), m.group(3), 2000);
        return null;
    }

    @Nullable
    private static LocalDate safeDate(String y, String mo, String d, int base) {
        try {
            return LocalDate.of(base + Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        } catch (Exception e) {
            return null;
        }
    }
}
