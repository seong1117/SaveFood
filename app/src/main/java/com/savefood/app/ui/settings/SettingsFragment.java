package com.savefood.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseUser;
import com.savefood.app.BuildConfig;
import com.savefood.app.R;
import com.savefood.app.auth.AuthHelper;

/** 설정: 계정 요약 + 계정 관리 화면 진입. (알림·기본 보관 기한 등 설정은 팀원 추가 예정) */
public class SettingsFragment extends Fragment {

    private TextView tvAccount;
    private TextView tvEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAccount = view.findViewById(R.id.tv_account);
        tvEmail = view.findViewById(R.id.tv_email);
        ((TextView) view.findViewById(R.id.tv_version)).setText("버전 " + BuildConfig.VERSION_NAME);

        view.findViewById(R.id.btn_account).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AccountActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        bind();   // 계정 화면에서 닉네임을 바꾸고 돌아왔을 때 반영
    }

    private void bind() {
        FirebaseUser user = AuthHelper.currentUser(requireContext());
        if (user == null) {
            tvAccount.setText("게스트");
            tvEmail.setText("로그인하면 데이터가 계정에 저장돼요");
            return;
        }
        String nickname = user.getDisplayName();
        tvAccount.setText(nickname != null && !nickname.trim().isEmpty() ? nickname : "닉네임 미설정");
        tvEmail.setText(user.getEmail());
    }
}
