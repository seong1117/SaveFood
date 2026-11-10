package com.savefood.app.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReceiptParserTest {

    /** 한 줄을 여러 칸(조각)으로 나눠 OCR 처럼 만든다. y = 줄 번호 * 40 */
    private static void row(List<OcrPiece> out, int y, String... cells) {
        int x = 0;
        for (String c : cells) {
            out.add(new OcrPiece(c, x, y * 40, x + 100, y * 40 + 30));
            x += 200;
        }
    }

    @Test
    public void martReceipt() {
        List<OcrPiece> p = new ArrayList<>();
        row(p, 0, "이마트 성수점");
        row(p, 1, "사업자 123-45-67890 대표 홍길동");
        row(p, 2, "TEL 02-123-4567");
        row(p, 3, "[판매] 2025-03-14 18:22");
        row(p, 4, "상품명", "단가", "수량", "금액");
        row(p, 5, "01 서울우유 1L", "2,980", "1", "2,980");
        row(p, 6, "02 * 양파(망)", "3,990", "2", "7,980");
        row(p, 7, "03 사과 4입", "9,900", "1", "9,900");
        row(p, 8, "8801115114154");
        row(p, 9, "할인", "-1,000");
        row(p, 10, "합계", "19,860");
        row(p, 11, "부가세", "1,805");
        row(p, 12, "신용카드", "19,860");

        ReceiptParser.Result r = ReceiptParser.parse(p);
        assertEquals("[서울우유 1L × 1, 양파(망) × 2, 사과 4입 × 1]", r.items.toString());
        assertEquals(LocalDate.of(2025, 3, 14), r.date);
    }

    @Test
    public void nameAndPriceOnSeparateRows() {
        List<String> rows = Arrays.asList("두부 300g", "8801234567890 1,500 1 1,500", "대파", "2,500");
        assertEquals("[두부 300g × 1, 대파 × 1]", ReceiptParser.findItems(rows).toString());
    }

    @Test
    public void quantityBeforeAmount() {
        List<String> rows = Arrays.asList("계란 30구 2 15,800", "바나나 4,500 3 13,500");
        assertEquals("[계란 30구 × 2, 바나나 × 3]", ReceiptParser.findItems(rows).toString());
    }

    @Test
    public void addressAndStoreAreNotItems() {
        List<String> rows = Arrays.asList("홈플러스 강서점", "서울특별시 강서구 공항대로 123", "02-987-6543");
        assertEquals("[]", ReceiptParser.findItems(rows).toString());
    }

    @Test
    public void dates() {
        assertEquals(LocalDate.of(2025, 1, 2), ReceiptParser.findDateIn("2025년 1월 2일"));
        assertEquals(LocalDate.of(2025, 11, 4), ReceiptParser.findDateIn("거래일시 25/11/04 09:12"));
        assertNull(ReceiptParser.findDateIn("2,980 1 2,980"));
    }

    @Test
    public void piecesOnSameLineAreJoinedLeftToRight() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(new OcrPiece("2,980", 300, 102, 380, 128));
        p.add(new OcrPiece("우유", 0, 100, 80, 130));
        p.add(new OcrPiece("합계", 0, 200, 80, 230));
        assertEquals(Arrays.asList("우유 2,980", "합계"), ReceiptParser.toRows(p));
    }
}
