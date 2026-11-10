package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 포장지·라벨 OCR 결과 → 소비기한(유통기한) 후보. 안드로이드 의존 없음(단위 테스트 가능).
 *
 * 1) OCR 이 숫자를 글자로 읽은 것을 고친다(O→0, l·I·|→1, S→5, B→8, Z→2). 숫자 옆에 있을 때만.
 * 2) 여러 날짜 표기를 LocalDate 로 바꾼다. DB 에는 toIso() 의 "YYYY-MM-DD" 로 넣는다.
 *    2026.11.20 / 2026-11-20 / 2026/11/20 / 2026년 11월 20일 / 20261120 / 26.11.20 / 26년 11월 20일
 *    20.11.2026(수입품) / 261120·11.20(기한 말이 붙었을 때만)
 * 3) 날짜 앞뒤의 말로 종류를 정한다. 소비기한·유통기한·EXP·"까지" = 기한, 제조일·포장일 = 기한 아님.
 *    "제조일로부터 12개월" 이 있으면 제조일에 더해 기한을 계산한다.
 * 4) 순서: 표시된 기한 → 계산한 기한 → 종류를 모르는 날짜(늦은 날짜 먼저). 최대 3개.
 */
public final class ExpiryDateParser {

    public enum Kind { EXPIRY, COMPUTED, UNLABELED }

    public static final class Candidate {
        public final LocalDate date;
        public final Kind kind;
        /** 화면에 보일 근거(예: "소비기한", "제조일+12개월", "날짜") */
        public final String note;

        Candidate(@NonNull LocalDate date, @NonNull Kind kind, @NonNull String note) {
            this.date = date;
            this.kind = kind;
            this.note = note;
        }

        @NonNull
        @Override
        public String toString() { return toIso(date) + " (" + note + ")"; }
    }

    /** 날짜 하나와 그 위치·종류(파싱 중간 결과). */
    static final class Found {
        final LocalDate date;
        final int start, end;
        Role role = Role.UNKNOWN;
        String keyword = "";

        Found(LocalDate date, int start, int end) {
            this.date = date;
            this.start = start;
            this.end = end;
        }
    }

    enum Role { EXPIRY, MADE, PACKED, UNKNOWN }

    private static final String[] EXPIRY_WORDS = {
            "소비기한", "유통기한", "유통기간", "품질유지기한", "품질유지", "상미기한", "기한",
            "EXP", "Exp", "exp", "BEST BEFORE", "Best Before", "best before"
    };
    private static final String[] MADE_WORDS = {
            "제조일자", "제조일", "제조", "생산일자", "생산일", "MFG", "Mfg", "MFD", "PROD"
    };
    private static final String[] PACKED_WORDS = {
            "포장일자", "포장일", "가공일자", "가공일", "도축일", "포장"
    };

    // 구분자: . - / , 또는 년·월. 날짜 사이 공백 허용
    private static final String SEP = "\\s*[.\\-/,]\\s*";
    private static final Pattern YMD_FULL = Pattern.compile(
            "(?<!\\d)(20\\d{2})(?:\\s*[.\\-/,년]\\s*|\\s+)(\\d{1,2})(?:\\s*[.\\-/,월]\\s*|\\s+)(\\d{1,2})\\s*일?(?!\\d)");
    private static final Pattern DMY_FULL = Pattern.compile(
            "(?<!\\d)(\\d{1,2})" + SEP + "(\\d{1,2})" + SEP + "(20\\d{2})(?!\\d)");
    private static final Pattern YMD_COMPACT = Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern YMD_SHORT = Pattern.compile(
            "(?<!\\d)(\\d{2})\\s*[.\\-/,년]\\s*(\\d{1,2})\\s*[.\\-/,월]\\s*(\\d{1,2})\\s*일?(?!\\d)");
    // 아래 둘은 오탐이 많아 기한 말이 붙었을 때만 쓴다
    private static final Pattern YMD_COMPACT6 = Pattern.compile("(?<!\\d)(\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern MD = Pattern.compile(
            "(?<![\\d.])(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?(?![\\d.])");

    // 제조일로부터 12개월 / 제조일부터 1년 / 제조 후 90일
    private static final Pattern PERIOD = Pattern.compile(
            "제조\\S{0,3}?\\s*(?:로부터|부터|후)\\s*(\\d{1,3})\\s*(개월|달|년|일)");

    // OCR 이 숫자를 비슷한 글자로 읽은 덩어리. 영문 단어 안에 있으면 건드리지 않는다
    private static final Pattern DIGIT_RUN = Pattern.compile("(?<![A-Za-z])[0-9OoIl|SBZ]+(?![A-Za-z])");

    private ExpiryDateParser() { }

    // ---------------- 공개 API ----------------

    /** OCR 조각 → 기한 후보(가능성 높은 순, 최대 3개). today 는 연도 추정과 범위 검사에 쓴다. */
    @NonNull
    public static List<Candidate> candidates(@NonNull List<OcrPiece> pieces, @NonNull LocalDate today) {
        return candidatesFromRows(ReceiptParser.toRows(pieces), today);
    }

    /** 글자 하나(예: "유통기한 26.11.20까지")를 "YYYY-MM-DD" 로. 기한이 없으면 null. */
    @Nullable
    public static String normalize(@NonNull String text, @NonNull LocalDate today) {
        List<String> rows = new ArrayList<>();
        Collections.addAll(rows, text.split("\\R"));
        List<Candidate> c = candidatesFromRows(rows, today);
        return c.isEmpty() ? null : toIso(c.get(0).date);
    }

    /** DB 저장 형식(ISO-8601, YYYY-MM-DD). */
    @NonNull
    public static String toIso(@NonNull LocalDate date) {
        return date.toString();
    }

    // ---------------- 단계별 처리 ----------------

    @NonNull
    static List<Candidate> candidatesFromRows(@NonNull List<String> rawRows, @NonNull LocalDate today) {
        List<Found> all = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        Role pending = Role.UNKNOWN;     // 이전 줄이 "소비기한" 같은 이름표뿐이면 다음 줄 날짜에 넘긴다
        String pendingWord = "";
        for (String raw : rawRows) {
            String row = fixOcr(raw);
            joined.append(row).append(' ');
            List<Found> found = findDates(row, today);
            if (found.isEmpty()) {
                Role label = labelOnly(row);
                pending = label;
                pendingWord = label == Role.UNKNOWN ? "" : lastKeyword(row, row.length());
                continue;
            }
            assignRoles(row, found);
            Found first = found.get(0);
            if (first.role == Role.UNKNOWN && pending != Role.UNKNOWN) {
                first.role = pending;
                first.keyword = pendingWord;
            }
            pending = Role.UNKNOWN;
            all.addAll(found);
        }
        return rank(all, joined.toString(), today);
    }

    /** 숫자 사이에 끼었거나 숫자에 붙은 O·l·S 등을 숫자로 바꾼다. 숫자가 하나도 없는 덩어리는 그대로 둔다. */
    @NonNull
    static String fixOcr(@NonNull String s) {
        Matcher m = DIGIT_RUN.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String run = m.group();
            int digits = 0;
            for (int i = 0; i < run.length(); i++) if (Character.isDigit(run.charAt(i))) digits++;
            out.append(s, last, m.start());
            if (digits == 0 || digits * 2 < run.length()) {
                out.append(run);              // 숫자보다 글자가 많으면 단어로 본다
            } else {
                out.append(run.replace('O', '0').replace('o', '0')
                        .replace('I', '1').replace('l', '1').replace('|', '1')
                        .replace('S', '5').replace('B', '8').replace('Z', '2'));
            }
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }

    /** 한 줄에서 날짜를 모두 찾는다(왼쪽부터, 겹치면 앞 규칙이 이긴다). */
    @NonNull
    static List<Found> findDates(@NonNull String row, @NonNull LocalDate today) {
        List<Found> out = new ArrayList<>();
        collect(out, YMD_FULL.matcher(row), m -> date(m.group(1), m.group(2), m.group(3), 0));
        collect(out, DMY_FULL.matcher(row), m -> dmy(m.group(1), m.group(2), m.group(3)));
        collect(out, YMD_COMPACT.matcher(row), m -> date(m.group(1), m.group(2), m.group(3), 0));
        collect(out, YMD_SHORT.matcher(row), m -> date(m.group(1), m.group(2), m.group(3), 2000));

        Matcher m6 = YMD_COMPACT6.matcher(row);
        while (m6.find()) {
            if (!hasExpiryContext(row, m6.start(), m6.end())) continue;
            add(out, date(m6.group(1), m6.group(2), m6.group(3), 2000), m6.start(), m6.end());
        }
        if (out.isEmpty()) {
            // 월·일만 있는 표기는 그 줄에 연도가 있는 날짜가 없을 때만
            Matcher md = MD.matcher(row);
            while (md.find()) {
                if (!hasExpiryContext(row, md.start(), md.end())) continue;
                add(out, monthDay(md.group(1), md.group(2), today), md.start(), md.end());
            }
        }
        out.sort(Comparator.comparingInt(f -> f.start));
        return out;
    }

    private interface DateOf { LocalDate of(Matcher m); }

    private static void collect(List<Found> out, Matcher m, DateOf f) {
        while (m.find()) add(out, f.of(m), m.start(), m.end());
    }

    private static void add(List<Found> out, @Nullable LocalDate d, int start, int end) {
        if (d == null) return;
        for (Found f : out) {
            if (start < f.end && f.start < end) return;   // 이미 찾은 날짜와 겹침
        }
        out.add(new Found(d, start, end));
    }

    /** 바로 앞에 기한 말이 있거나 바로 뒤에 "까지"가 있는가. */
    private static boolean hasExpiryContext(String row, int start, int end) {
        String before = row.substring(0, start).replaceAll("[\\s:：]+$", "");
        for (String w : EXPIRY_WORDS) if (before.endsWith(w)) return true;
        return row.substring(end).trim().startsWith("까지");
    }

    /** 날짜마다 앞(이전 날짜 뒤부터) 또는 바로 뒤의 말로 종류를 정한다. */
    static void assignRoles(@NonNull String row, @NonNull List<Found> found) {
        for (int i = 0; i < found.size(); i++) {
            Found f = found.get(i);
            int from = i == 0 ? 0 : found.get(i - 1).end;
            String before = row.substring(from, f.start);
            String after = row.substring(f.end, i + 1 < found.size() ? found.get(i + 1).start : row.length()).trim();

            String word = lastKeyword(before, before.length());
            if (!word.isEmpty()) {
                f.role = roleOf(word);
                f.keyword = word;
            } else if (after.startsWith("까지")) {
                f.role = Role.EXPIRY;
                f.keyword = "까지";
            } else if (after.startsWith("제조")) {
                f.role = Role.MADE;
                f.keyword = "제조";
            } else if (after.startsWith("포장")) {
                f.role = Role.PACKED;
                f.keyword = "포장";
            }
        }
    }

    /** text 의 limit 앞에서 가장 뒤에 나온 기한·제조·포장 말. 없으면 "". 길게 맞는 말을 고른다. */
    @NonNull
    private static String lastKeyword(String text, int limit) {
        String best = "";
        int bestAt = -1;
        for (String[] words : new String[][] { EXPIRY_WORDS, MADE_WORDS, PACKED_WORDS }) {
            for (String w : words) {
                int at = text.lastIndexOf(w, limit - w.length());
                if (at < 0) continue;
                int end = at + w.length();
                // "제조원", "포장재질" 처럼 뒤에 한글이 붙은 짧은 말은 날짜 이름표가 아니다
                if (w.length() == 2 && end < text.length() && isHangul(text.charAt(end))) continue;
                if (end > bestAt || (end == bestAt && w.length() > best.length())) {
                    best = w;
                    bestAt = end;
                }
            }
        }
        return best;
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }

    private static Role roleOf(String word) {
        for (String w : EXPIRY_WORDS) if (w.equals(word)) return Role.EXPIRY;
        for (String w : MADE_WORDS) if (w.equals(word)) return Role.MADE;
        return Role.PACKED;
    }

    /** 날짜 없이 이름표만 있는 줄이면 그 종류. */
    private static Role labelOnly(String row) {
        String word = lastKeyword(row, row.length());
        return word.isEmpty() ? Role.UNKNOWN : roleOf(word);
    }

    @NonNull
    private static List<Candidate> rank(List<Found> all, String joined, LocalDate today) {
        LocalDate min = today.minusYears(1), max = today.plusYears(10);
        List<Candidate> expiry = new ArrayList<>(), computed = new ArrayList<>(), unlabeled = new ArrayList<>();

        // "제조일로부터 N개월" 이 있고 제조일 표시가 없으면, 이름표 없는 날짜는 제조일이다
        Matcher period = PERIOD.matcher(joined);
        boolean hasPeriod = period.find();
        boolean hasMade = false;
        for (Found f : all) if (f.role == Role.MADE) hasMade = true;

        for (Found f : all) {
            Role role = f.role;
            if (role == Role.UNKNOWN && hasPeriod && !hasMade) role = Role.MADE;
            if (role == Role.EXPIRY) {
                expiry.add(new Candidate(f.date, Kind.EXPIRY, noteFor(f.keyword)));
            } else if (role == Role.MADE && hasPeriod) {
                int n = Integer.parseInt(period.group(1));
                String unit = period.group(2);
                LocalDate d = "년".equals(unit) ? f.date.plusYears(n)
                        : "일".equals(unit) ? f.date.plusDays(n) : f.date.plusMonths(n);
                computed.add(new Candidate(d, Kind.COMPUTED, "제조일+" + n + unit));
            } else if (role == Role.UNKNOWN) {
                unlabeled.add(new Candidate(f.date, Kind.UNLABELED, "날짜"));
            }
        }
        // 종류를 모르면 늦은 날짜가 기한일 가능성이 높다(제조일은 기한보다 앞)
        unlabeled.sort((a, b) -> b.date.compareTo(a.date));

        List<Candidate> out = new ArrayList<>();
        for (List<Candidate> group : Arrays.asList(expiry, computed, unlabeled)) {   // List.of 는 API 30+
            for (Candidate c : group) {
                if (c.date.isBefore(min) || c.date.isAfter(max)) continue;
                boolean dup = false;
                for (Candidate o : out) if (o.date.equals(c.date)) dup = true;
                if (!dup) out.add(c);
                if (out.size() == 3) return out;
            }
        }
        return out;
    }

    private static String noteFor(String keyword) {
        if (keyword.equals("까지")) return "까지";
        if (keyword.startsWith("유통")) return "유통기한";
        if (keyword.startsWith("품질") || keyword.equals("상미기한")) return "품질유지기한";
        if (keyword.equalsIgnoreCase("EXP") || keyword.equalsIgnoreCase("BB")
                || keyword.equalsIgnoreCase("best before")) return "EXP";
        return "소비기한";
    }

    // ---------------- 날짜 만들기 ----------------

    @Nullable
    private static LocalDate date(String y, String mo, String d, int base) {
        try {
            return LocalDate.of(base + Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        } catch (Exception e) {
            return null;   // 13월, 2월 30일 같은 잘못 읽은 값
        }
    }

    /** 일.월.연(수입품). 앞 숫자가 12보다 크면 확실히 일, 뒤 숫자가 12보다 크면 월.일.연으로 본다. */
    @Nullable
    private static LocalDate dmy(String a, String b, String y) {
        int first = Integer.parseInt(a), second = Integer.parseInt(b);
        if (second > 12 && first <= 12) return date(y, a, b, 0);
        return date(y, b, a, 0);
    }

    /** 연도 없는 월·일: 올해로 보되, 반년 넘게 지났으면 내년. */
    @Nullable
    private static LocalDate monthDay(String mo, String d, LocalDate today) {
        LocalDate date = date(String.valueOf(today.getYear()), mo, d, 0);
        if (date == null) return null;
        return date.isBefore(today.minusMonths(6)) ? date.plusYears(1) : date;
    }
}
