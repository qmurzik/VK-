package re.sova.qmods;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Full-screen photo viewer of the messenger: pinch / double-tap zoom, swipe sideways between photos, down to close. */
final class Qvk3ImViewer {
    private Qvk3ImViewer() {
    }

    static void show(Activity a, List<Qvk3ImModel.Attachment> photos, int start) {
        if (photos.isEmpty()) return;
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(Color.BLACK);
        Zoom img = new Zoom(a);
        root.addView(img, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 28), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 8));
        bar.setBackgroundColor(0x66000000);
        bar.addView(Qvk3Ui.icon(a, Qvk3Icons.CLOSE, Color.WHITE, 48, 22, "Закрыть", v -> d.dismiss()));
        TextView counter = Qvk3Ui.line(a, "", 16, Color.WHITE, true);
        bar.addView(counter, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(Qvk3Ui.icon(a, Qvk3Icons.DOWNLOAD, Color.WHITE, 48, 22, "Сохранить в галерею", v -> save(a, photos.get(img.index))));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        img.photos = photos;
        img.counter = counter;
        img.dialog = d;
        img.chrome = bar;
        img.open(start);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        d.show();
    }

    /** Saves the original to Pictures/QVK through the system download manager (visible in the gallery). */
    static void save(Activity a, Qvk3ImModel.Attachment p) {
        String url = p.url.isEmpty() ? p.preview : p.url;
        try {
            android.app.DownloadManager dm = a.getSystemService(android.app.DownloadManager.class);
            android.app.DownloadManager.Request r = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "QVK/vk_" + p.ownerId + "_" + p.id + ".jpg");
            r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            dm.enqueue(r);
            Qvk3Ui.toast(a, "Сохраняем в «Изображения/QVK»");
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось сохранить: " + Qvk3Jobs.message(e));
        }
    }

    static final class Zoom extends ImageView {
        List<Qvk3ImModel.Attachment> photos;
        TextView counter;
        Dialog dialog;
        View chrome;
        int index;
        private final Matrix m = new Matrix();
        private float scale = 1f, dragY;
        private final ScaleGestureDetector scaler;
        private final GestureDetector gestures;

        Zoom(Activity a) {
            super(a);
            setScaleType(ScaleType.MATRIX);
            scaler = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float f = Math.max(1f / scale, Math.min(5f / scale, d.getScaleFactor()));
                    scale *= f;
                    m.postScale(f, f, d.getFocusX(), d.getFocusY());
                    clamp();
                    return true;
                }
            });
            gestures = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    chrome.setVisibility(chrome.getVisibility() == VISIBLE ? GONE : VISIBLE);
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.05f) fit();
                    else {
                        scale = 2.5f;
                        m.postScale(2.5f, 2.5f, e.getX(), e.getY());
                        clamp();
                    }
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (scale > 1.05f) {
                        m.postTranslate(-dx, -dy);
                        clamp();
                    } else if (e1 != null && Math.abs(e2.getY() - e1.getY()) > Math.abs(e2.getX() - e1.getX())) {
                        dragY = e2.getY() - e1.getY();
                        setTranslationY(dragY);
                        ((View) getParent()).setBackgroundColor(Color.argb((int) (255 * Math.max(0.2f, 1f - Math.abs(dragY) / getHeight())), 0, 0, 0));
                    }
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (scale > 1.05f || e1 == null) return false;
                    float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(vx) > 600) {
                        if (dx < 0 && index + 1 < photos.size()) open(index + 1);
                        else if (dx > 0 && index > 0) open(index - 1);
                        return true;
                    }
                    return false;
                }
            });
        }

        void open(int i) {
            index = i;
            Qvk3ImModel.Attachment p = photos.get(i);
            counter.setText(photos.size() > 1 ? (i + 1) + " из " + photos.size() : "");
            Bitmap small = Qvk3Images.get(getContext()).cachedAny(p.preview, 900);
            if (small != null) setImageBitmap(small);
            String url = p.url.isEmpty() ? p.preview : p.url;
            setTag(url);
            Qvk3Images.get(getContext()).load(url, 2048, b -> {
                if (b != null && url.equals(getTag())) {
                    setImageBitmap(b);
                    fit();
                }
            });
            post(this::fit);
        }

        void fit() {
            if (getDrawable() == null || getWidth() == 0) return;
            float dw = getDrawable().getIntrinsicWidth(), dh = getDrawable().getIntrinsicHeight();
            float s = Math.min(getWidth() / dw, getHeight() / dh);
            m.reset();
            m.postScale(s, s);
            m.postTranslate((getWidth() - dw * s) / 2f, (getHeight() - dh * s) / 2f);
            scale = 1f;
            setImageMatrix(m);
        }

        void clamp() {
            if (getDrawable() == null) return;
            RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
            m.mapRect(r);
            float dx = 0, dy = 0;
            if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
            else if (r.left > 0) dx = -r.left;
            else if (r.right < getWidth()) dx = getWidth() - r.right;
            if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
            else if (r.top > 0) dy = -r.top;
            else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
            m.postTranslate(dx, dy);
            setImageMatrix(m);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            gestures.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (Math.abs(dragY) > getHeight() * 0.18f) dialog.dismiss();
                else if (dragY != 0) {
                    animate().translationY(0).setDuration(180).start();
                    ((View) getParent()).setBackgroundColor(Color.BLACK);
                }
                dragY = 0;
            }
            return true;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            fit();
        }
    }
}
