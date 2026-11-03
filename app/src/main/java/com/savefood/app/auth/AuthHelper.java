package com.savefood.app.auth;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
}
