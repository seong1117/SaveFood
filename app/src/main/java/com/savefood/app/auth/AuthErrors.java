package com.savefood.app.auth;

import androidx.annotation.Nullable;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Firebase 인증 오류를 사용자에게 보여줄 한글 메시지로 변환한다. */
public final class AuthErrors {
    private AuthErrors() { }

    public static String message(@Nullable Exception e) {
        if (e == null) return "알 수 없는 오류가 발생했어요";

        if (e instanceof FirebaseNetworkException) return "네트워크 연결을 확인해 주세요";
        if (e instanceof FirebaseTooManyRequestsException) return "시도가 너무 많아요. 잠시 후 다시 시도해 주세요";
        // WeakPassword 는 InvalidCredentials 의 하위 클래스이므로 먼저 검사
        if (e instanceof FirebaseAuthWeakPasswordException) return "비밀번호가 너무 약해요. 더 복잡하게 설정해 주세요";
        if (e instanceof FirebaseAuthUserCollisionException) return "이미 가입된 이메일이에요";
        if (e instanceof FirebaseAuthRecentLoginRequiredException) return "보안을 위해 다시 로그인한 뒤 시도해 주세요";

        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) return "사용이 중지된 계정이에요";
            return "이메일 또는 비밀번호가 올바르지 않아요";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) return "이메일 형식이 올바르지 않아요";
            return "이메일 또는 비밀번호가 올바르지 않아요";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "이메일/비밀번호 로그인이 비활성화되어 있어요 (Firebase 콘솔 확인)";
            }
        }
        return "오류가 발생했어요: " + e.getMessage();
    }
}
