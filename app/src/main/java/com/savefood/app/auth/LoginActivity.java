package com.savefood.app.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.savefood.app.MainActivity;
import com.savefood.app.R;

import java.util.HashMap;
import java.util.Map;

/** 이메일/비밀번호 로그인·회원가입 화면. (앱 시작 화면) */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnSignUp;
    private TextView tvNotice;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSignUp = findViewById(R.id.btn_signup);
        tvNotice = findViewById(R.id.tv_notice);

        if (!AuthHelper.isFirebaseReady(this)) {
            tvNotice.setVisibility(View.VISIBLE);
        }

        btnLogin.setOnClickListener(v -> login());
        btnSignUp.setOnClickListener(v -> signUp());
        findViewById(R.id.tv_reset).setOnClickListener(v -> resetPassword());
        findViewById(R.id.tv_guest).setOnClickListener(v -> goMain());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 이미 로그인한 상태면 바로 메인으로
        if (AuthHelper.currentUser(this) != null) goMain();
    }

    private boolean checkFirebase() {
        if (AuthHelper.isFirebaseReady(this)) return true;
        Toast.makeText(this, "Firebase 설정(google-services.json)이 필요해요. 지금은 '둘러보기'를 이용하세요.",
                Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean validate(String email, String password, boolean needPassword) {
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("올바른 이메일을 입력하세요");
            return false;
        }
        if (needPassword && (password == null || password.length() < 6)) {
            etPassword.setError("비밀번호는 6자 이상이어야 해요");
            return false;
        }
        return true;
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void login() {
        if (!checkFirebase()) return;
        String email = text(etEmail);
        String password = text(etPassword);
        if (!validate(email, password, true)) return;

        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goMain();
                    } else {
                        Toast.makeText(this, "로그인 실패: " + errorMessage(task.getException()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signUp() {
        if (!checkFirebase()) return;
        String email = text(etEmail);
        String password = text(etPassword);
        if (!validate(email, password, true)) return;

        setLoading(true);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        createUserDocument(task.getResult().getUser());
                        Toast.makeText(this, "회원가입 완료!", Toast.LENGTH_SHORT).show();
                        goMain();
                    } else {
                        Toast.makeText(this, "회원가입 실패: " + errorMessage(task.getException()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void resetPassword() {
        if (!checkFirebase()) return;
        String email = text(etEmail);
        if (!validate(email, null, false)) return;

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> Toast.makeText(this,
                        task.isSuccessful() ? "비밀번호 재설정 메일을 보냈어요" : "메일 전송 실패: " + errorMessage(task.getException()),
                        Toast.LENGTH_LONG).show());
    }

    /** users/{uid} 문서 생성 (프로필·설정 저장용). Firestore 보안 규칙 설정 필요. */
    private void createUserDocument(@Nullable FirebaseUser user) {
        if (user == null) return;
        Map<String, Object> doc = new HashMap<>();
        doc.put("email", user.getEmail());
        doc.put("createdAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .set(doc)
                .addOnFailureListener(e -> Log.w(TAG, "users 문서 생성 실패", e));
    }

    private String errorMessage(@Nullable Exception e) {
        return e == null || e.getMessage() == null ? "알 수 없는 오류" : e.getMessage();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnSignUp.setEnabled(!loading);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
