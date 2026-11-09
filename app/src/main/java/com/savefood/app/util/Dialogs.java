package com.savefood.app.util;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.savefood.app.R;
import com.savefood.app.auth.AuthValidator;

/** 입력창이 있는 다이얼로그 모음. 입력이 잘못되면 닫히지 않고 오류를 표시한다. */
public final class Dialogs {
    private Dialogs() { }

    public interface OnInput {
        /** 입력이 잘못됐으면 오류 메시지를, 정상이면 null 을 반환 (null 이면 다이얼로그가 닫힘) */
        @Nullable
        String onSubmit(String value);
    }

    public interface OnPasswordChange {
        void onSubmit(String currentPassword, String newPassword);
    }

    /** 한 줄 입력 다이얼로그 (닉네임 변경, 비밀번호 확인, 이메일 입력 등) */
    public static void input(Context context, String title, @Nullable String message, String hint,
                             int inputType, @Nullable String initial, String positiveText, OnInput callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null);
        TextInputLayout til = view.findViewById(R.id.til_input);
        TextInputEditText et = view.findViewById(R.id.et_input);

        boolean isPassword = (inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
        til.setHint(hint);
        et.setInputType(inputType);
        if (isPassword) til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        if (initial != null) {
            et.setText(initial);
            et.setSelection(initial.length());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(positiveText, null)   // 클릭 처리는 아래에서 직접 연결 (자동 닫힘 방지)
                .setNegativeButton("취소", null);
        if (message != null) builder.setMessage(message);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            til.setError(null);
            String raw = et.getText() == null ? "" : et.getText().toString();
            String error = callback.onSubmit(isPassword ? raw : raw.trim());
            if (error != null) til.setError(error);
            else dialog.dismiss();
        }));
        dialog.show();
    }

    /** 비밀번호 변경 다이얼로그 (현재 / 새 비밀번호 / 확인) */
    public static void changePassword(Context context, OnPasswordChange callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_password, null);
        TextInputLayout tilCurrent = view.findViewById(R.id.til_current);
        TextInputLayout tilNew = view.findViewById(R.id.til_new);
        TextInputLayout tilConfirm = view.findViewById(R.id.til_confirm);
        TextInputEditText etCurrent = view.findViewById(R.id.et_current);
        TextInputEditText etNew = view.findViewById(R.id.et_new);
        TextInputEditText etConfirm = view.findViewById(R.id.et_confirm);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("비밀번호 변경")
                .setView(view)
                .setPositiveButton("변경하기", null)
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilCurrent.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);

            String current = text(etCurrent);
            String newPw = text(etNew);
            String confirm = text(etConfirm);
            boolean ok = true;

            if (current.isEmpty()) {
                tilCurrent.setError("현재 비밀번호를 입력하세요");
                ok = false;
            }
            String error = AuthValidator.password(newPw);
            if (error != null) {
                tilNew.setError(error);
                ok = false;
            } else if (newPw.equals(current)) {
                tilNew.setError("현재 비밀번호와 다르게 설정하세요");
                ok = false;
            }
            if (!newPw.equals(confirm)) {
                tilConfirm.setError("새 비밀번호가 일치하지 않아요");
                ok = false;
            }
            if (!ok) return;

            dialog.dismiss();
            callback.onSubmit(current, newPw);
        }));
        dialog.show();
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }
}
