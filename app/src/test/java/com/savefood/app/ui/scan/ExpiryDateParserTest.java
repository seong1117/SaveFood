package com.savefood.app.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExpiryDateParserTest {

    // 연도 추정·범위 검사가 날짜에 따라 달라지지 않게 오늘을 고정한다
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static String norm(String text) {
        return ExpiryDateParser.normalize(text, TODAY);
    }

    private static List<ExpiryDateParser.Candidate> rows(String... rows) {
        return ExpiryDateParser.candidatesFromRows(Arrays.asList(rows), TODAY);
    }

    @Test
    public void manyFormatsBecomeIso() {
        assertEquals("2026-11-20", norm("2026.11.20"));
        assertEquals("2026-11-20", norm("2026-11-20"));
        assertEquals("2026-11-20", norm("2026/11/20"));
        assertEquals("2026-11-20", norm("2026. 11. 20."));
        assertEquals("2026-11-20", norm("2026,11,20"));          // 점을 쉼표로 읽은 경우
        assertEquals("2026-11-20", norm("2026 11 20"));
        assertEquals("2026-11-20", norm("2026년 11월 20일"));
        assertEquals("2026-11-20", norm("20261120"));
        assertEquals("2026-11-20", norm("26.11.20"));
        assertEquals("2026-11-20", norm("26년 11월 20일"));
        assertEquals("2026-11-20", norm("20.11.2026"));          // 수입품 일.월.연
        assertEquals("2026-11-20", norm("11/20/2026"));          // 월/일/연(일이 12보다 큼)
        assertEquals("2026-01-05", norm("EXP 2026/01/05"));
    }

    @Test
    public void shortFormsNeedExpiryWord() {
        assertEquals("2026-11-20", norm("유통기한 261120"));
        assertNull(norm("261120"));                               // 로트 번호일 수 있음
        assertEquals("2026-11-20", norm("11.20까지"));
        assertEquals("2026-11-20", norm("11월 20일까지"));
        assertNull(norm("11.20"));
        // 연도가 없으면 올해, 반년 넘게 지났으면 내년
        assertEquals("2027-01-05", norm("소비기한: 1/5"));
        assertEquals("2026-11-01", norm("소비기한: 11/1"));       // 이미 지난 기한도 그대로
    }

    @Test
    public void fixesLettersReadAsDigits() {
        assertEquals("2026-11-20", norm("2O26.1l.2O"));
        assertEquals("2026-01-15", norm("소비기한 26.O1.I5"));
        assertEquals("2026-11-20", norm("2026.11.2O까지"));
        assertEquals("BEST BEFORE", ExpiryDateParser.fixOcr("BEST BEFORE"));
        assertEquals("SOS 1L", ExpiryDateParser.fixOcr("SOS 1L"));
    }

    @Test
    public void manufactureAndPackDatesAreNotExpiry() {
        List<ExpiryDateParser.Candidate> c = rows("제조일자 2026.11.01", "소비기한 2026.12.01");
        assertEquals(1, c.size());
        assertEquals(LocalDate.of(2026, 12, 1), c.get(0).date);
        assertEquals(ExpiryDateParser.Kind.EXPIRY, c.get(0).kind);

        c = rows("제조일 2026.11.01 유통기한 2026.12.31");
        assertEquals("[2026-12-31 (유통기한)]", c.toString());

        c = rows("포장일 26.11.08", "소비기한 26.11.14");          // 마트 정육 라벨
        assertEquals("[2026-11-14 (소비기한)]", c.toString());

        c = rows("26.11.01 제조");
        assertTrue(c.isEmpty());
    }

    @Test
    public void labelOnPreviousLine() {
        assertEquals("[2026-11-25 (소비기한)]", rows("소비기한", "2026.11.25").toString());
        // "제조원"은 제조일 이름표가 아니다
        assertEquals("[2026-11-25 (날짜)]", rows("제조원: 행복식품", "2026.11.25").toString());
    }

    @Test
    public void computedFromManufactureDate() {
        assertEquals("[2027-03-01 (제조일+12개월)]",
                rows("제조일로부터 12개월까지", "제조일자 2026.03.01").toString());
        assertEquals("[2026-12-30 (제조일+90일)]",
                rows("제조일 2026.10.01", "제조 후 90일").toString());
        // 기간 안내가 있고 제조일 표시가 없으면, 이름표 없는 날짜는 제조일이다
        assertEquals("[2027-03-01 (제조일+1년)]",
                rows("제조일로부터 1년", "2026.03.01").toString());
    }

    @Test
    public void unlabeledLaterDateFirst() {
        List<ExpiryDateParser.Candidate> c = rows("2026.11.01 2026.12.15");
        assertEquals(LocalDate.of(2026, 12, 15), c.get(0).date);
        assertEquals(LocalDate.of(2026, 11, 1), c.get(1).date);
    }

    @Test
    public void labeledBeatsUnlabeledAndAtMostThree() {
        List<ExpiryDateParser.Candidate> c = rows(
                "2026.12.31", "2026.12.30", "2026.12.29", "소비기한 2026.11.20", "2026.11.20");
        assertEquals(3, c.size());
        assertEquals("2026-11-20 (소비기한)", c.get(0).toString());
        assertEquals(LocalDate.of(2026, 12, 31), c.get(1).date);
    }

    @Test
    public void notDates() {
        assertTrue(rows("TEL 02-123-4567", "010-1234-5678").isEmpty());
        assertTrue(rows("2,980원", "100g당 1,245원", "1.5L").isEmpty());
        assertTrue(rows("8801115114154").isEmpty());              // 바코드 번호
        assertTrue(rows("행사기간 11/1~11/30").isEmpty());
        assertTrue(rows("2026.13.40", "2026.02.30").isEmpty());   // 없는 날짜
        assertTrue(rows("1998.01.01", "2045.01.01").isEmpty());   // 범위 밖
    }

    @Test
    public void isoForDatabase() {
        assertEquals("2026-01-05", ExpiryDateParser.toIso(LocalDate.of(2026, 1, 5)));
    }

    @Test
    public void ocrPiecesOnSameRowAreJoined() {
        // 이름표와 날짜를 OCR 이 따로 읽어도 같은 줄로 묶는다
        List<OcrPiece> p = new ArrayList<>();
        p.add(new OcrPiece("소비기한", 0, 100, 120, 130));
        p.add(new OcrPiece("2026.11.20", 200, 102, 400, 132));
        p.add(new OcrPiece("제조일자", 0, 150, 120, 180));
        p.add(new OcrPiece("2026.11.01", 200, 150, 400, 180));
        List<ExpiryDateParser.Candidate> c = ExpiryDateParser.candidates(p, TODAY);
        assertEquals("[2026-11-20 (소비기한)]", c.toString());
    }
}
