package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** A chat opened from QVK's own conversation list (VK's chat screen hosts the same view when VK opens a chat). */
public final class Qvk3ImChatActivity extends Activity {
    static final String PEER = "peer";
    private Qvk3ImChatView view;

    static void open(Activity from, long peer) {
        from.startActivity(new Intent(from, Qvk3ImChatActivity.class).putExtra(PEER, peer));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        long peer = getIntent().getLongExtra(PEER, 0);
        if (peer == 0) {
            finish();
            return;
        }
        Qvk3Ui.Palette p = Qvk3Sheet.palette(this);
        Qvk3Ui.bars(this, p);
        view = new Qvk3ImChatView(this, peer, this::finish);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(p.bg);
        root.addView(view);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
            v.setPadding(in.getSystemWindowInsetLeft(), top, in.getSystemWindowInsetRight(), bottom);
            return in.consumeSystemWindowInsets();
        });
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (!view.back()) finish();
                });
    }

    @Override
    public void onBackPressed() {
        if (view != null && view.back()) return;
        super.onBackPressed();
    }
}
