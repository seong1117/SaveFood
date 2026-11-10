package com.savefood.app.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PriceTagParserTest {

    /** top 위치와 글자 높이로 한 줄을 만든다. */
    private static OcrPiece line(String text, int top, int height) {
        return new OcrPiece(text, 0, top, 300, top + height);
    }

    @Test
    public void biggestNameWinsOverPriceAndPromo() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(line("[행사] 2+1", 0, 20));
        p.add(line("서울우유 1L", 30, 48));
        p.add(line("2,980원", 90, 80));          // 가격이 가장 크지만 품명이 아님
        p.add(line("(100ml당 298원)", 180, 18));
        p.add(line("원산지: 국산", 200, 18));
        p.add(line("행사기간 11/1~11/30", 220, 18));
        p.add(line("8801115114154", 240, 16));
        assertEquals("서울우유 1L", PriceTagParser.names(p).get(0));
    }

    @Test
    public void nameAndPriceOnOneLine() {
        assertEquals("깐마늘 200g", PriceTagParser.clean("깐마늘 200g 4,980원"));
        assertEquals("국산 콩나물", PriceTagParser.clean("[특가] 국산 콩나물"));
    }

    @Test
    public void nonNameLines() {
        assertNull(PriceTagParser.clean("2,980원"));
        assertNull(PriceTagParser.clean("100g당 1,245원"));
        assertNull(PriceTagParser.clean("행사기간 11/1~11/30"));
        assertNull(PriceTagParser.clean("원산지 국산"));
        assertNull(PriceTagParser.clean("1+1"));
    }

    /** 왼쪽 위치까지 정한 조각. */
    private static OcrPiece box(String text, int left, int top, int width, int height) {
        return new OcrPiece(text, left, top, left + width, top + height);
    }

    @Test
    public void candidatesAreDistinctAndAtMostThree() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(line("CJ 햇반", 0, 40));
        p.add(line("CJ 햇반", 50, 40));
        p.add(line("백미밥 210g", 100, 30));     // 비슷한 크기로 바로 아래 → 같은 품명
        p.add(line("즉석밥 12입", 140, 20));      // 너무 작음 → 다른 후보
        p.add(line("맛있는 쌀밥", 170, 10));
        assertEquals("[CJ 햇반 백미밥 210g, CJ 햇반, 즉석밥 12입]", PriceTagParser.names(p).toString());
    }

    @Test
    public void ocrNoiseSymbolsAndRepeatsAreRemoved() {
        assertEquals("미역", PriceTagParser.clean("'미역 ^미역"));
        assertEquals("완도미역", PriceTagParser.clean("완도미역 ^미역"));
        assertEquals("서울우유 1L", PriceTagParser.clean("ㅣ 서울우유 | 1L"));
        assertEquals("서울우유", PriceTagParser.clean("I 서울우유 \""));
        assertEquals("우유 1 L", PriceTagParser.clean("우유 1 L"));          // 숫자 뒤 단위는 남김
        assertEquals("하림 닭가슴살 1.5kg", PriceTagParser.clean("하림 닭가슴살 1.5kg~"));
        assertEquals("미역", PriceTagParser.names(Arrays.asList(line("'미역 ^미역", 0, 40))).get(0));
    }

    @Test
    public void longNameSplitOverTwoLinesIsJoined() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(line("CJ제일제당 비비고", 0, 40));
        p.add(line("왕교자 1.05kg", 46, 40));
        p.add(line("12,980원", 100, 70));
        p.add(line("(100g당 1,236원)", 175, 16));
        List<String> names = PriceTagParser.names(p);
        assertEquals("CJ제일제당 비비고 왕교자 1.05kg", names.get(0));
        assertEquals("CJ제일제당 비비고", names.get(1));    // 이은 것이 틀렸을 때 고를 수 있게
    }

    @Test
    public void nameSplitIntoSideBySideCellsIsJoined() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(box("기장", 0, 0, 80, 40));
        p.add(box("돌미역 100g", 110, 2, 200, 40));
        p.add(box("산지직송", 0, 50, 90, 14));             // 작은 글자는 잇지 않는다
        p.add(box("4,980원", 400, 0, 160, 60));
        List<String> names = PriceTagParser.names(p);
        assertEquals("기장 돌미역 100g", names.get(0));
        assertEquals("[기장 돌미역 100g, 돌미역 100g, 산지직송]", names.toString());
    }

    @Test
    public void farApartLinesStaySeparate() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(line("국산콩 두부", 0, 40));
        p.add(line("부침용 300g", 200, 40));     // 같은 크기지만 멀리 떨어짐(다른 가격표)
        assertEquals("[국산콩 두부, 부침용 300g]", PriceTagParser.names(p).toString());
    }
}
