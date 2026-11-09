package com.savefood.app.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.savefood.app.R;
import com.savefood.app.auth.AuthErrors;
import com.savefood.app.auth.AuthHelper;
import com.savefood.app.auth.AuthValidator;
import com.savefood.app.data.UserRepository;
import com.savefood.app.util.Dialogs;
import com.savefood.app.util.UiUtils;

/**
 * 계정 관리: 프로필(닉네임), 이메일 인증, 비밀번호 변경, 로그아웃, 회원 탈퇴.
 * 비밀번호 변경·탈퇴는 보안상 현재 비밀번호로 재인증한 뒤 진행한다.
 */
public class AccountActivity extends AppCompatActivity {

    private FirebaseUser user;
    private View layoutMember, layoutGuest, btnVerify;
    private TextView tvAvatar, tvNickname, tvEmail, tvVerified;
    private LinearProgressIndicator progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        layoutMember = findViewById(R.id.layout_member);
        layoutGuest = findViewById(R.id.layout_guest);
        tvAvatar = findViewById(R.id.tv_avatar);
        tvNickname = findViewById(R.id.tv_nickname);
        tvEmail = findViewById(R.id.tv_email);
        tvVerified = findViewById(R.id.tv_verified);
        btnVerify = findViewById(R.id.btn_verify);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_go_login).setOnClickListener(v -> AuthHelper.openLogin(this));
        btnVerify.setOnClickListener(v -> sendVerificationMail());
        findViewById(R.id.btn_nickname).setOnClickListener(v -> showNicknameDialog());
        findViewById(R.id.btn_password).setOnClickListener(v -> showPasswordDialog());
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            AuthHelper.signOut(this);
            AuthHelper.openLogin(this);
        });
        findViewById(R.id.btn_withdraw).setOnClickListener(v -> confirmWithdraw());
    }

    @Override
    protected void onResume() {
        super.onResume();
        user = AuthHelper.currentUser(this);
        bind();
        if (user != null) {
            // 메일 인증을 마치고 돌아왔을 때 상태를 최신으로 갱신
            user.reload().addOnCompleteListener(t -> {
                user = AuthHelper.currentUser(this);
                bind();
            });
        }
    }

    // ---------------- 화면 표시 ----------------
    private void bind() {
        if (user == null) {
            layoutMember.setVisibility(View.GONE);
            layoutGuest.setVisibility(View.VISIBLE);
            return;
        }
        layoutMember.setVisibility(View.VISIBLE);
        layoutGuest.setVisibility(View.GONE);

        String nickname = user.getDisplayName();
        boolean hasNickname = nickname != null && !nickname.trim().isEmpty();
        tvNickname.setText(hasNickname ? nickname : "닉네임을 설정해 주세요");
        // 이모지 등 서로게이트 문자가 깨지지 않도록 코드포인트 단위로 첫 글자 추출
        tvAvatar.setText(hasNickname
                ? new String(Character.toChars(nickname.trim().codePointAt(0))) : "🙂");
        tvEmail.setText(user.getEmail());

        boolean verified = user.isEmailVerified();
        tvVerified.setText(verified ? "✔ 이메일 인증 완료" : "이메일 미인증");
        tvVerified.setBackgroundResource(verified ? R.drawable.bg_badge_green : R.drawable.bg_badge_red);
        tvVerified.setTextColor(getColor(verified ? R.color.green_dark : R.color.red));
        btnVerify.setVisibility(verified ? View.GONE : View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ---------------- 이메일 인증 ----------------
    private void sendVerificationMail() {
        if (user == null) return;
        setLoading(true);
        user.sendEmailVerification().addOnCompleteListener(t -> {
            setLoading(false);
            toast(t.isSuccessful()
                    ? "인증 메일을 보냈어요. 메일의 링크를 누른 뒤 이 화면으로 돌아오세요."
                    : AuthErrors.message(t.getException()));
        });
    }

    // ---------------- 닉네임 변경 ----------------
    private void showNicknameDialog() {
        if (user == null) return;
        Dialogs.input(this, "닉네임 변경", null, "닉네임 (2~12자)", InputType.TYPE_CLASS_TEXT,
                user.getDisplayName(), "저장", value -> {
                    String error = AuthValidator.nickname(value);
                    if (error != null) return error;
                    saveNickname(value);
                    return null;
                });
    }

    private void saveNickname(String nickname) {
        setLoading(true);
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(nickname)
                .build();
        user.updateProfile(request).addOnCompleteListener(t -> {
            setLoading(false);
            if (t.isSuccessful()) {
                UserRepository.get().updateNickname(user.getUid(), nickname);
                bind();
                toast("닉네임을 변경했어요");
            } else {
                toast(AuthErrors.message(t.getException()));
            }
        });
    }

    // ---------------- 비밀번호 변경 ----------------
    private void showPasswordDialog() {
        if (user == null) return;
        Dialogs.changePassword(this, (current, newPassword) ->
                reauthenticate(current, () ->
                        user.updatePassword(newPassword).addOnCompleteListener(t -> {
                            setLoading(false);
                            toast(t.isSuccessful() ? "비밀번호를 변경했어요" : AuthErrors.message(t.getException()));
                        })));
    }

    // ---------------- 회원 탈퇴 ----------------
    private void confirmWithdraw() {
        if (user == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("회원 탈퇴")
                .setMessage("탈퇴하면 계정과 저장된 식재료·남은 음식 데이터가 모두 삭제되고 되돌릴 수 없어요.\n계속할까요?")
                .setPositiveButton("계속", (d, w) -> askPasswordForWithdraw())
                .setNegativeButton("취소", null)
                .show();
    }

    private void askPasswordForWithdraw() {
        Dialogs.input(this, "비밀번호 확인", "본인 확인을 위해 현재 비밀번호를 입력하세요.", "비밀번호",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null, "탈퇴하기", password -> {
                    if (password.isEmpty()) return "비밀번호를 입력하세요";
                    withdraw(password);
                    return null;
                });
    }

    private void withdraw(String password) {
        reauthenticate(password, () -> {
            String uid = user.getUid();
            // 1) Firestore 데이터 삭제 → 2) 인증 계정 삭제 (순서 중요: 계정이 먼저 사라지면 데이터를 지울 권한이 없음)
            UserRepository.get().deleteUserData(uid, error -> {
                if (error != null) {
                    setLoading(false);
                    toast("데이터 삭제에 실패했어요 (Firestore 규칙/네트워크 확인): " + error.getMessage());
                    return;
                }
                user.delete().addOnCompleteListener(t -> {
                    setLoading(false);
                    if (t.isSuccessful()) {
                        toast("탈퇴가 완료되었어요. 이용해 주셔서 감사합니다.");
                        AuthHelper.openLogin(this);
                    } else {
                        toast(AuthErrors.message(t.getException()));
                    }
                });
            });
        });
    }

    // ---------------- 재인증 ----------------
    /** 현재 비밀번호로 재인증한 뒤 onSuccess 실행. 로딩 표시는 onSuccess 쪽에서 끝낸다. */
    private void reauthenticate(String password, Runnable onSuccess) {
        if (user == null || user.getEmail() == null) {
            toast("이메일 계정만 지원해요");
            return;
        }
        setLoading(true);
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                onSuccess.run();
            } else {
                setLoading(false);
                Exception e = t.getException();
                toast(e instanceof FirebaseAuthInvalidCredentialsException
                        ? "비밀번호가 올바르지 않아요" : AuthErrors.message(e));
            }
        });
    }
}
