package io.github.playgta5.launcher.utils;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;

import io.github.playgta5.launcher.R;

public class ErrorDialog extends Dialog {

    private final String title;
    private final String summary;
    private final Throwable throwable;

    public ErrorDialog(@NonNull Context context, String title, String summary, Throwable throwable) {
        super(context, R.style.Theme_GTA5Launcher);
        this.title = title;
        this.summary = summary;
        this.throwable = throwable;
    }

    public static void show(Context context, String title, String summary, Throwable throwable) {
        try {
            new ErrorDialog(context, title, summary, throwable).show();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_error);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView textTitle = findViewById(R.id.text_error_title);
        TextView textSummary = findViewById(R.id.text_error_summary);
        TextView textDetails = findViewById(R.id.text_error_details);

        if (title != null) textTitle.setText(title);
        if (summary != null) textSummary.setText(summary);

        String fullTrace = "";
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            fullTrace = sw.toString();
        }
        textDetails.setText(fullTrace.isEmpty() ? "No additional debug trace available." : fullTrace);

        final String copyContent = (title != null ? title : "Error") + "\n" +
                (summary != null ? summary : "") + "\n\n" + fullTrace;

        Button btnCopy = findViewById(R.id.btn_copy_error);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("GTA5 Launch Error", copyContent));
                    Toast.makeText(getContext(), "Error details copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button btnDismiss = findViewById(R.id.btn_dismiss_error);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> dismiss());
        }
    }
}

