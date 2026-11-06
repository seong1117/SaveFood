package com.savefood.app.ui.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
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

    @Test
    public void candidatesAreDistinctAndAtMostThree() {
        List<OcrPiece> p = new ArrayList<>();
        p.add(line("CJ 햇반", 0, 40));
        p.add(line("CJ 햇반", 50, 40));
        p.add(line("백미밥 210g", 100, 30));
        p.add(line("즉석밥 12입", 140, 20));
        p.add(line("맛있는 쌀밥", 170, 10));
        assertEquals("[CJ 햇반, 백미밥 210g, 즉석밥 12입]", PriceTagParser.names(p).toString());
    }
}
