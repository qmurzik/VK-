package com.vkmod.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vkmod.core.ModLog;
import com.vkmod.feat.UiKit;

/** Shows the mod log for diagnostics. */
public class LogActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final UiKit.Colors col = UiKit.colors(this);
        UiKit.applyStatusBar(this, col);
        LinearLayout root = UiKit.vbox(this);
        root.setBackgroundColor(col.bg);
        int p = UiKit.dp(this, 12);
        root.setPadding(p, p + UiKit.dp(this, 8), p, p);
        root.addView(UiKit.text(this, "Лог работы мода", 22, col.text, true));

        final TextView body = UiKit.text(this, "", 11, col.text, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        refresh(body);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = p;
        root.addView(sv, lp);

        LinearLayout bar = UiKit.hbox(this);
        bar.setGravity(Gravity.END);
        bar.addView(UiKit.button(this, "Копировать", col, v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("vkmod log", ModLog.dump()));
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
        }));
        bar.addView(UiKit.button(this, "Очистить", col, v -> { ModLog.clear(); refresh(body); }));
        root.addView(bar);
        setContentView(root);
    }

    private void refresh(TextView t) { String s = ModLog.dump(); t.setText(s.isEmpty() ? "(пусто)" : s); }
}
