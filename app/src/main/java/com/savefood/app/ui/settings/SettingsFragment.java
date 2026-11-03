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
import com.savefood.app.auth.LoginActivity;

/** 설정: 계정 정보 · 로그아웃 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = AuthHelper.currentUser(requireContext());
        TextView tvAccount = view.findViewById(R.id.tv_account);
        tvAccount.setText(user != null && user.getEmail() != null ? user.getEmail() : "게스트 (로그인 안 됨)");

        ((TextView) view.findViewById(R.id.tv_version)).setText("버전 " + BuildConfig.VERSION_NAME);

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            AuthHelper.signOut(requireContext());
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
