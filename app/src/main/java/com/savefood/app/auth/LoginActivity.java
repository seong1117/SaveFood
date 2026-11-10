package com.savefood.app.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.savefood.app.R;
import com.savefood.app.util.Dialogs;

/** 로그인 화면 (앱 시작 화면). 회원가입은 SignUpActivity, 비밀번호 찾기는 다이얼로그로 처리. */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnSignUp;
    private LinearProgressIndicator progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSignUp = findViewById(R.id.btn_signup);
        progress = findViewById(R.id.progress);
        TextView tvNotice = findViewById(R.id.tv_notice);

        if (!AuthHelper.isFirebaseReady(this)) {
            tvNotice.setVisibility(View.VISIBLE);
        }

        btnLogin.setOnClickListener(v -> login());
        btnSignUp.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        findViewById(R.id.tv_reset).setOnClickListener(v -> showResetDialog());
        findViewById(R.id.tv_guest).setOnClickListener(v -> AuthHelper.openMain(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 이미 로그인한 상태면 바로 메인으로
        if (AuthHelper.currentUser(this) != null) AuthHelper.openMain(this);
    }

    private boolean checkFirebase() {
        if (AuthHelper.isFirebaseReady(this)) return true;
        Toast.makeText(this, "Firebase 설정(google-services.json)이 필요해요. 지금은 '둘러보기'를 이용하세요.",
                Toast.LENGTH_LONG).show();
        return false;
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void login() {
        if (!checkFirebase()) return;
        String email = text(etEmail);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        String error = AuthValidator.email(email);
        if (error != null) {
            etEmail.setError(error);
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("비밀번호를 입력하세요");
            return;
        }

        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        AuthHelper.openMain(this);
                    } else {
                        Toast.makeText(this, AuthErrors.message(task.getException()), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** 비밀번호 찾기: 이메일 입력 → 재설정 메일 발송 */
    private void showResetDialog() {
        if (!checkFirebase()) return;
        Dialogs.input(this, "비밀번호 찾기", "가입한 이메일로 비밀번호 재설정 링크를 보내 드려요.",
                "이메일", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                text(etEmail), "메일 보내기", email -> {
                    String error = AuthValidator.email(email);
                    if (error != null) return error;

                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> Toast.makeText(this,
                                    task.isSuccessful()
                                            // 계정 존재 여부는 보안상 알려주지 않는다 (이메일 열거 보호)
                                            ? "가입된 이메일이라면 재설정 메일을 보냈어요. 스팸함도 확인해 보세요."
                                            : AuthErrors.message(task.getException()),
                                    Toast.LENGTH_LONG).show());
                    return null;
                });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        btnLogin.setEnabled(!loading);
        btnSignUp.setEnabled(!loading);
    }
}
