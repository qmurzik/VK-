package re.sova.qmods;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

/**
 * IDEA-0012: long press on a tab of VK's bottom bar → «Настроить нижнее меню» opens the QVK dock editor directly
 * (a strip of the dock's icons: drag to reorder, «Добавить вкладку», move/remove), instead of VK's own list editor.
 * Called from VK's BottomActionPopupDelegate (patch 0021); returning false keeps VK's editor.
 *
 * The icons of the strip jiggle while the editor is open (iOS-style, ±4°) so it is obvious they can be dragged — the
 * tester's feedback was that the editor did not look editable.
 */
public final class Qvk3DockQuick implements Application.ActivityLifecycleCallbacks {
    private static Qvk3DockQuick watcher;

    private Qvk3DockQuick() {
    }

    public static boolean open(Context c) {
        try {
            while (c instanceof ContextWrapper && !(c instanceof Activity)) c = ((ContextWrapper) c).getBaseContext();
            if (!(c instanceof Activity)) return false;
            Activity a = (Activity) c;
            synchronized (Qvk3DockQuick.class) {
                if (watcher == null) {
                    watcher = new Qvk3DockQuick();
                    a.getApplication().registerActivityLifecycleCallbacks(watcher);
                }
            }
            SettingsEditorActivity.open(a, "dock");
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.dockQuick", t);
            return false;
        }
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (!dockEditor(a)) return;
        View root = a.getWindow().getDecorView();
        root.postDelayed(() -> jiggle(root), 350);
        // the editor rebuilds the strip after every change: start the new icons too (one listener per window)
        if (root.getTag(0x51A6C0DF) != null) return;
        root.setTag(0x51A6C0DF, Boolean.TRUE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (a.isFinishing() || a.isDestroyed()) {
                    root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    return;
                }
                jiggle(root);
            }
        });
    }

    static boolean dockEditor(Activity a) {
        return a instanceof SettingsEditorActivity && a.getIntent() != null && "dock".equals(a.getIntent().getStringExtra("screen"));
    }

    static void jiggle(View root) {
        HorizontalScrollView strip = find(root);
        if (strip == null || strip.getChildCount() == 0 || !(strip.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) strip.getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            View icon = row.getChildAt(i);
            if (icon.getTag(0x51A6C0DE) != null) continue;
            ObjectAnimator an = ObjectAnimator.ofFloat(icon, View.ROTATION, -4f, 4f);
            an.setDuration(120 + (i % 3) * 14L);
            an.setRepeatCount(ValueAnimator.INFINITE);
            an.setRepeatMode(ValueAnimator.REVERSE);
            an.setStartDelay((i * 37L) % 110);
            icon.setTag(0x51A6C0DE, an);
            an.start();
        }
    }

    static HorizontalScrollView find(View v) {
        if (v instanceof HorizontalScrollView) return (HorizontalScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                HorizontalScrollView h = find(g.getChildAt(i));
                if (h != null) return h;
            }
        }
        return null;
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (!dockEditor(a)) return;
        HorizontalScrollView strip = find(a.getWindow().getDecorView());
        if (strip == null || strip.getChildCount() == 0 || !(strip.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) strip.getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            Object an = row.getChildAt(i).getTag(0x51A6C0DE);
            if (an instanceof ObjectAnimator) ((ObjectAnimator) an).cancel();
            row.getChildAt(i).setTag(0x51A6C0DE, null);
            row.getChildAt(i).setRotation(0f);
        }
    }

    @Override
    public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityStopped(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override
    public void onActivityDestroyed(Activity a) {
    }
}
