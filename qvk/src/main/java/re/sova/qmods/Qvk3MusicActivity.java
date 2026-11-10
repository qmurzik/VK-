package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * Standalone host of {@link Qvk3MusicScreen} (shortcut, notification, mini-player, full-screen player opened
 * from the embedded music tab, and file pickers that need activity results).
 */
public final class Qvk3MusicActivity extends Activity {
    static final String EXTRA_TAB = "qvk3_tab";
    static final String EXTRA_PLAYER = "qvk3_open_player";
    /** Show only the player; closing it finishes the activity (used from the embedded tab and mini-player). */
    static final String EXTRA_PLAYER_ONLY = "qvk3_player_only";
    static final String EXTRA_IMPORT = "qvk3_import";
    static final String[] TABS = {"Главная", "Моя музыка", "Плейлисты", "Поиск", "Загрузки"};
    private Qvk3MusicScreen screen;
    private Object backCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent i = getIntent();
        boolean playerOnly = i.getBooleanExtra(EXTRA_PLAYER_ONLY, false);
        int tab = state != null ? state.getInt(EXTRA_TAB, 0) : i.getIntExtra(EXTRA_TAB, i.getBooleanExtra(EXTRA_IMPORT, false) ? 4 : -1);
        boolean player = state != null ? state.getBoolean("player") : playerOnly || i.getBooleanExtra(EXTRA_PLAYER, false);
        screen = new Qvk3MusicScreen(this, false, tab, player && Qvk3MusicEngine.get(this).current() != null);
        if (playerOnly) {
            if (Qvk3MusicEngine.get(this).current() == null) {
                finish();
                return;
            }
            screen.onPlayerClosed = this::finish;
        }
        setContentView(screen);
        if (state == null && i.getBooleanExtra(EXTRA_IMPORT, false)) screen.importFiles();
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::back;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (screen != null && (intent.getBooleanExtra(EXTRA_PLAYER, false) || intent.getBooleanExtra(EXTRA_PLAYER_ONLY, false))) screen.openPlayer();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (screen == null) return;
        out.putInt(EXTRA_TAB, screen.tab());
        out.putBoolean("player", screen.playerShown());
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (screen != null) screen.result(req, res, data);
    }

    private void back() {
        if (screen == null || !screen.handleBack()) finish();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        }
        super.onDestroy();
    }
}
