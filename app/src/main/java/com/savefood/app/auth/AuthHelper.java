package com.savefood.app.auth;

import android.content.Context;
import android.content.Intent;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.savefood.app.MainActivity;

/**
 * Firebase 설정(google-services.json)이 없는 환경에서도 앱이 죽지 않도록
 * FirebaseAuth 접근을 감싸는 헬퍼.
 */
public final class AuthHelper {
    private AuthHelper() { }

    public static boolean isFirebaseReady(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    /** 로그인된 사용자 (게스트이거나 Firebase 미설정이면 null) */
    public static FirebaseUser currentUser(Context context) {
        return isFirebaseReady(context) ? FirebaseAuth.getInstance().getCurrentUser() : null;
    }

    public static void signOut(Context context) {
        if (isFirebaseReady(context)) FirebaseAuth.getInstance().signOut();
    }

    /** 로그인 화면으로 이동하며 기존 화면 스택을 모두 비운다. */
    public static void openLogin(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    /** 메인 화면으로 이동하며 기존 화면 스택을 모두 비운다. */
    public static void openMain(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
