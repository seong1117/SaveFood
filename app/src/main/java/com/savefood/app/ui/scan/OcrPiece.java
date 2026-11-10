package com.savefood.app.ui.scan;

import androidx.annotation.NonNull;

/** OCR 한 조각(ML Kit Text.Line)의 글자와 위치(픽셀). 파서들이 함께 쓴다. */
public final class OcrPiece {
    final String text;
    final int left, top, right, bottom;

    public OcrPiece(@NonNull String text, int left, int top, int right, int bottom) {
        this.text = text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    float centerY() { return (top + bottom) / 2f; }

    /** 글자 높이 ≈ 글자 크기 */
    int height() { return Math.max(1, bottom - top); }
}
