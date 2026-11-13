package re.sova.qmods;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Plays a «кружок» (VK video message) the way VK does: a big round video over a dimmed chat with a progress ring
 * around it; tap the circle to pause/resume, tap outside or «Назад» to close. VK's transcript is shown below.
 */
final class Qvk3ImCircle {
    private Qvk3ImCircle() {
    }

    static void play(Activity a, Qvk3ImModel.Attachment x) {
        if (x.url == null || x.url.isEmpty() || !x.url.startsWith("http")) {
            Qvk3Ui.toast(a, "ВК не отдал файл видеосообщения");
            return;
        }
        Qvk3ImVoice.stop();   // one sound at a time
        Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0xCC000000);
        int size = Math.min(a.getResources().getDisplayMetrics().widthPixels - Qvk3Ui.dp(a, 48), Qvk3Ui.dp(a, 340));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout stage = new FrameLayout(a);
        Ring ring = new Ring(a);
        stage.addView(ring, new FrameLayout.LayoutParams(size, size));
        FrameLayout clip = new FrameLayout(a);
        clip.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        clip.setClipToOutline(true);
        clip.setBackgroundColor(Color.BLACK);
        int inset = Qvk3Ui.dp(a, 8);
        ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (x.preview != null && !x.preview.isEmpty()) Qvk3Images.get(a).load(x.preview, 600, b -> {
            if (b != null) poster.setImageBitmap(b);
        });
        TextureView tv = new TextureView(a);
        clip.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        clip.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(size - inset * 2, size - inset * 2, Gravity.CENTER);
        stage.addView(clip, cl);
        col.addView(stage, new LinearLayout.LayoutParams(size, size));
        if (x.transcript != null && !x.transcript.isEmpty()) {
            TextView t = Qvk3Ui.text(a, x.transcript, 15, 0xE6FFFFFF, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Qvk3Ui.dp(a, 24), Qvk3Ui.dp(a, 18), Qvk3Ui.dp(a, 24), 0);
            col.addView(t);
        }
        root.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        MediaPlayer mp = new MediaPlayer();
        final boolean[] prepared = {false};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                try {
                    int dur = mp.getDuration();
                    if (prepared[0] && dur > 0) ring.set(mp.getCurrentPosition() / (float) dur);
                } catch (IllegalStateException ignored) {
                }
                ring.postDelayed(this, 50);
            }
        };
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int wi, int he) {
                try {
                    mp.setSurface(new Surface(st));
                    mp.setDataSource(x.url);
                    mp.setOnPreparedListener(p -> {
                        prepared[0] = true;
                        // the server keeps the camera's aspect: crop it to fill the circle
                        int vw = p.getVideoWidth(), vh = p.getVideoHeight();
                        if (vw > 0 && vh > 0) {
                            float s = Math.max(1f, Math.max(vw, vh) / (float) Math.min(vw, vh));
                            tv.setScaleX(vw >= vh ? s : 1f);
                            tv.setScaleY(vh > vw ? s : 1f);
                        }
                        p.start();
                        poster.animate().alpha(0f).setDuration(200).start();
                        ring.post(tick);
                    });
                    mp.setOnCompletionListener(p -> {
                        ring.set(1f);
                        poster.animate().alpha(1f).setDuration(200).start();
                    });
                    mp.setOnErrorListener((p, what, extra) -> {
                        Qvk3Ui.toast(a, "Видеосообщение не воспроизводится");
                        return true;
                    });
                    mp.prepareAsync();
                } catch (Exception e) {
                    Qvk3Ui.toast(a, "Видеосообщение не воспроизводится: " + Qvk3Jobs.message(e));
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int wi, int he) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        stage.setOnClickListener(v -> {
            if (!prepared[0]) return;
            try {
                if (mp.isPlaying()) mp.pause();
                else {
                    if (ring.progress >= 0.999f) mp.seekTo(0);
                    mp.start();
                    poster.animate().alpha(0f).setDuration(150).start();
                }
            } catch (IllegalStateException ignored) {
            }
        });
        root.setOnClickListener(v -> d.dismiss());
        d.setOnDismissListener(x2 -> {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
        });
        d.show();
        stage.setScaleX(0.6f);
        stage.setScaleY(0.6f);
        stage.setAlpha(0f);
        stage.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    /** Progress ring around the circle. */
    static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        float progress;

        Ring(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(4 * d);
            track.setColor(0x33FFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(4 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(Color.WHITE);
        }

        void set(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = arc.getStrokeWidth() / 2 + 1;
            r.set(s, s, getWidth() - s, getHeight() - s);
            c.drawArc(r, 0, 360, false, track);
            c.drawArc(r, -90, 360 * progress, false, arc);
        }
    }
}
