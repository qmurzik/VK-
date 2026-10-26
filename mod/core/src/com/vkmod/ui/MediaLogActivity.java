package com.vkmod.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vkmod.feat.MediaLog;
import com.vkmod.feat.UiKit;

import java.util.List;

/** Media seen by the app (photos, videos, clips, stories, voice): download or copy the direct link. */
public class MediaLogActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final UiKit.Colors col = UiKit.colors(this);
        UiKit.applyStatusBar(this, col);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(col.bg);
        LinearLayout root = UiKit.vbox(this);
        int p = UiKit.dp(this, 16);
        root.setPadding(p, p + UiKit.dp(this, 8), p, p);
        sv.addView(root);
        root.addView(UiKit.text(this, "Журнал медиа", 24, col.text, true));
        root.addView(UiKit.text(this, "Откройте фото, видео, клип, историю или голосовое в VK — ссылка появится здесь. Включите нужные виды в настройках «Медиа».", 13, col.text2, false));

        List<MediaLog.Item> items = MediaLog.snapshot();
        if (items.isEmpty()) {
            TextView e = UiKit.text(this, "Пока пусто", 16, col.text2, false);
            e.setPadding(0, UiKit.dp(this, 24), 0, 0);
            root.addView(e);
        }
        for (final MediaLog.Item it : items) {
            LinearLayout card = UiKit.vbox(this);
            card.setBackground(UiKit.round(col.card, 14, this));
            card.setPadding(p, UiKit.dp(this, 10), p, UiKit.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = UiKit.dp(this, 10);
            card.setLayoutParams(lp);
            card.addView(UiKit.text(this, label(it.kind) + (it.ref.isEmpty() ? "" : " · " + it.ref), 14, col.text, true));
            String shown = it.url.length() > 90 ? it.url.substring(0, 90) + "…" : it.url;
            card.addView(UiKit.text(this, shown, 11, col.text2, false));
            LinearLayout bar = UiKit.hbox(this);
            bar.setGravity(Gravity.END);
            bar.addView(UiKit.button(this, "Скачать", col, v -> {
                String err = MediaLog.download(this, it);
                Toast.makeText(this, err == null ? "Загрузка начата, файл в Загрузки/VK" : "Ошибка: " + err, Toast.LENGTH_LONG).show();
            }));
            bar.addView(UiKit.button(this, "Ссылка", col, v -> {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", it.url));
                Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
            }));
            card.addView(bar);
            root.addView(card);
        }
        if (!items.isEmpty()) {
            LinearLayout bar = UiKit.hbox(this);
            bar.setGravity(Gravity.CENTER);
            bar.setPadding(0, p, 0, 0);
            bar.addView(UiKit.button(this, "Очистить журнал", col, v -> { MediaLog.clear(); recreate(); }));
            root.addView(bar);
        }
        setContentView(sv);
    }

    private static String label(String k) {
        switch (k) {
            case "photo": return "Фото (оригинал)";
            case "video": return "Видео";
            case "clip": return "Клип";
            case "story_photo": return "Фото из истории";
            case "story_video": return "Видео из истории";
            case "voice": return "Голосовое сообщение";
            default: return k;
        }
    }
}
