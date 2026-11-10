package com.savefood.app.auth;

import android.util.Patterns;

import androidx.annotation.Nullable;

/** 입력값 검증. 오류가 있으면 사용자에게 보여줄 메시지를, 정상이면 null 을 반환한다. */
public final class AuthValidator {
    private AuthValidator() { }

    @Nullable
    public static String email(@Nullable String email) {
        if (email == null || email.isEmpty()) return "이메일을 입력하세요";
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "올바른 이메일 형식이 아니에요";
        return null;
    }

    /** 8자 이상, 영문과 숫자를 모두 포함 (정책 변경 시 이 메서드만 수정) */
    @Nullable
    public static String password(@Nullable String pw) {
        if (pw == null || pw.length() < 8) return "비밀번호는 8자 이상이어야 해요";
        boolean letter = false;
        boolean digit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) return "영문과 숫자를 함께 사용해 주세요";
        return null;
    }

    @Nullable
    public static String nickname(@Nullable String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 12) {
            return "닉네임은 2~12자로 입력하세요";
        }
        return null;
    }
}
