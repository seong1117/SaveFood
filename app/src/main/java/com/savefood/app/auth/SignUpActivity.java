package com.savefood.app.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.savefood.app.R;
import com.savefood.app.data.UserRepository;

/**
 * 회원가입: 닉네임·이메일·비밀번호 입력 → 계정 생성 → 닉네임 저장 → 인증 메일 발송 → users/{uid} 문서 생성
 * (이메일 인증을 하지 않아도 앱 사용은 가능. 강제하려면 로그인 후 isEmailVerified() 검사를 추가하세요.)
 */
public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout tilNickname, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etNickname, etEmail, etPassword, etConfirm;
    private MaterialCheckBox cbTerms;
    private MaterialButton btnSignUp;
    private LinearProgressIndicator progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        tilNickname = findViewById(R.id.til_nickname);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirm = findViewById(R.id.til_confirm);
        etNickname = findViewById(R.id.et_nickname);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm);
        cbTerms = findViewById(R.id.cb_terms);
        btnSignUp = findViewById(R.id.btn_signup);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSignUp.setOnClickListener(v -> submit());
    }

    private String text(TextInputEditText et, boolean trim) {
        String s = et.getText() == null ? "" : et.getText().toString();
        return trim ? s.trim() : s;
    }

    private void submit() {
        tilNickname.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        String nickname = text(etNickname, true);
        String email = text(etEmail, true);
        String password = text(etPassword, false);
        String confirm = text(etConfirm, false);

        boolean ok = true;
        String error = AuthValidator.nickname(nickname);
        if (error != null) { tilNickname.setError(error); ok = false; }
        error = AuthValidator.email(email);
        if (error != null) { tilEmail.setError(error); ok = false; }
        error = AuthValidator.password(password);
        if (error != null) { tilPassword.setError(error); ok = false; }
        if (!password.equals(confirm)) { tilConfirm.setError("비밀번호가 일치하지 않아요"); ok = false; }
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "약관에 동의해 주세요", Toast.LENGTH_SHORT).show();
            ok = false;
        }
        if (!ok) return;

        if (!AuthHelper.isFirebaseReady(this)) {
            Toast.makeText(this, "Firebase 설정(google-services.json)이 필요해요", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult().getUser() == null) {
                        setLoading(false);
                        Toast.makeText(this, AuthErrors.message(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    onAccountCreated(task.getResult().getUser(), nickname);
                });
    }

    private void onAccountCreated(FirebaseUser user, String nickname) {
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(nickname)
                .build();
        user.updateProfile(request).addOnCompleteListener(t -> {
            user.sendEmailVerification();                       // 인증 메일 (실패해도 가입은 유지)
            UserRepository.get().saveNewUser(user, nickname);   // users/{uid} 프로필 문서
            setLoading(false);
            Toast.makeText(this, "가입 완료! 인증 메일을 보냈어요", Toast.LENGTH_LONG).show();
            AuthHelper.openMain(this);
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        btnSignUp.setEnabled(!loading);
    }
}
