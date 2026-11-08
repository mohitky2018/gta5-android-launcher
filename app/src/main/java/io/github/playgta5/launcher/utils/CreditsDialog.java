package io.github.playgta5.launcher.utils;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;

import io.github.playgta5.launcher.R;

public class CreditsDialog extends Dialog {

    public CreditsDialog(@NonNull Context context) {
        super(context, R.style.Theme_GTA5Launcher);
    }

    public static void show(Context context) {
        try {
            new CreditsDialog(context).show();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_credits);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Button btnDismiss = findViewById(R.id.btn_dismiss_credits);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> dismiss());
        }
    }
}
