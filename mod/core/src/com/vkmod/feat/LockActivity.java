package com.vkmod.feat;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;

/** Full-screen PIN / biometric gate shown over VK when the app lock is enabled. */
public class LockActivity extends Activity {
    private final StringBuilder entered = new StringBuilder();
    private TextView dots, hint;
    private UiKit.Colors col;
    private CancellationSignal cancel;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        col = UiKit.colors(this);
        UiKit.applyStatusBar(this, col);

        LinearLayout root = UiKit.vbox(this);
        root.setBackgroundColor(col.bg);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = UiKit.dp(this, 24);
        root.setPadding(p, UiKit.dp(this, 64), p, p);

        TextView title = UiKit.text(this, "Введите PIN-код", 22, col.text, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        hint = UiKit.text(this, " ", 14, col.text2, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        dots = UiKit.text(this, "", 34, col.accent, false);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, UiKit.dp(this, 24), 0, UiKit.dp(this, 24));
        root.addView(dots);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        for (String k : keys) {
            TextView t = UiKit.text(this, k, 24, col.text, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(UiKit.round(col.card, 36, this));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = UiKit.dp(this, 72);
            lp.height = UiKit.dp(this, 72);
            lp.setMargins(UiKit.dp(this, 10), UiKit.dp(this, 8), UiKit.dp(this, 10), UiKit.dp(this, 8));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> onKey(k));
            grid.addView(t);
        }
        root.addView(grid);

        if (Registry.isOn("app_lock_bio") && Build.VERSION.SDK_INT >= 28) {
            TextView bio = UiKit.text(this, "Войти по биометрии", 14, col.accent, true);
            bio.setGravity(Gravity.CENTER);
            bio.setPadding(0, UiKit.dp(this, 20), 0, 0);
            bio.setOnClickListener(v -> startBiometric());
            root.addView(bio);
        }
        setContentView(root);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (Registry.isOn("app_lock_bio") && Build.VERSION.SDK_INT >= 28) startBiometric();
    }

    @Override protected void onPause() {
        super.onPause();
        if (cancel != null) { try { cancel.cancel(); } catch (Throwable ignored) { } cancel = null; }
    }

    @Override public void onBackPressed() { moveTaskToBack(true); }

    private void onKey(String k) {
        if (k.equals("⌫")) { if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1); }
        else if (k.equals("OK")) { submit(); return; }
        else if (entered.length() < 8) entered.append(k);
        render();
        if (entered.length() >= 4 && AppLock.check(entered.toString())) success();
    }

    private void submit() {
        if (AppLock.check(entered.toString())) success();
        else { hint.setText("Неверный PIN-код"); entered.setLength(0); render(); }
    }

    private void render() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entered.length(); i++) sb.append("● ");
        dots.setText(sb.length() == 0 ? " " : sb.toString().trim());
    }

    private void success() { Lifecycle.unlock(); finish(); overridePendingTransition(0, 0); }

    private void startBiometric() {
        if (Build.VERSION.SDK_INT < 28 || cancel != null) return;
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Вход в VK")
                    .setNegativeButton("PIN-код", getMainExecutor(), (d, w) -> { })
                    .build();
            cancel = new CancellationSignal();
            prompt.authenticate(cancel, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { success(); }
                @Override public void onAuthenticationError(int code, CharSequence msg) { cancel = null; }
                @Override public void onAuthenticationFailed() { }
            });
        } catch (Throwable t) {
            cancel = null;
            ModLog.w("biometric unavailable", t);
        }
    }
}
