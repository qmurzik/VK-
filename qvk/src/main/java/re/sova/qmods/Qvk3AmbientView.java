package re.sova.qmods;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.view.View;

/**
 * «Живой фон» of the player: four large soft blobs in the colours of the cover drift slowly while the track plays and
 * freeze on pause; colours flow into the next cover's over ~0.9 s. When VK already holds the microphone permission
 * (voice messages), the blobs also breathe with the bass of QVK Music's own output (android Visualizer on the
 * player's audio session — nothing is recorded); without it the motion is just the slow drift.
 * Draws at most ~30 fps and only while attached, visible and playing; weak devices get a still picture.
 */
final class Qvk3AmbientView extends View {
    private static final int N = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix m = new Matrix();
    private final int[] from = new int[N], to = new int[N], cur = new int[N];
    private final RadialGradient[] shaders = new RadialGradient[N];
    private final int[] shaderColor = new int[N];
    private long colorStart = -1, lastFrame;
    private float t;             // animation time, seconds (advances only while playing)
    private float energy, energyTarget;
    private boolean playing, still;
    private int base = 0xFF0D0F14;
    private Visualizer vis;
    private int session;

    Qvk3AmbientView(Context c) {
        super(c);
        still = Qvk3Appearance.weakDevice(c);
        int[] def = {0xFF2B3140, 0xFF1D2433, 0xFF3A3150, 0xFF14202A};
        System.arraycopy(def, 0, from, 0, N);
        System.arraycopy(def, 0, to, 0, N);
        System.arraycopy(def, 0, cur, 0, N);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Takes the colours of a new cover (null = neutral). */
    void setCover(Bitmap b) {
        int[] c = b == null ? new int[]{0xFF2B3140, 0xFF1D2433, 0xFF3A3150, 0xFF14202A} : palette(b);
        System.arraycopy(cur, 0, from, 0, N);
        System.arraycopy(c, 0, to, 0, N);
        base = Qvk3Ui.deep(c[0], 0.12f);
        colorStart = System.nanoTime();
        invalidate();
    }

    void setPlaying(boolean on, int audioSession) {
        playing = on;
        session = audioSession;
        if (on) startVisualizer();
        else stopVisualizer();
        lastFrame = 0;
        invalidate();
    }

    /** Four region colours, saturated and darkened so white text stays readable on top. */
    static int[] palette(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 16, 16, true);
        int[] out = new int[N];
        int[][] regions = {{0, 0}, {8, 0}, {0, 8}, {8, 8}};
        for (int r = 0; r < N; r++) {
            long rr = 0, gg = 0, bb = 0;
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++) {
                    int p = s.getPixel(regions[r][0] + x, regions[r][1] + y);
                    rr += Color.red(p);
                    gg += Color.green(p);
                    bb += Color.blue(p);
                }
            int c = Color.rgb((int) (rr / 64), (int) (gg / 64), (int) (bb / 64));
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.35f + 0.08f);
            hsv[2] = Math.max(0.28f, Math.min(0.62f, hsv[2]));
            out[r] = Color.HSVToColor(hsv);
        }
        if (s != src) s.recycle();
        return out;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = System.nanoTime();
        float k = colorStart < 0 ? 1f : Math.min(1f, (now - colorStart) / 900_000_000f);
        k = k * k * (3 - 2 * k);
        for (int i = 0; i < N; i++) cur[i] = blend(from[i], to[i], k);
        if (k >= 1f) colorStart = -1;
        if (playing && !still) {
            float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
            t += dt * (0.18f + energy * 0.5f);
            energy += (energyTarget - energy) * Math.min(1f, dt * 6f);
        }
        lastFrame = now;
        c.drawColor(base);
        float r0 = Math.max(w, h) * 0.62f;
        for (int i = 0; i < N; i++) {
            if (shaders[i] == null || shaderColor[i] != cur[i]) {
                shaders[i] = new RadialGradient(0, 0, 1f, new int[]{cur[i], (cur[i] & 0x00FFFFFF) | 0x66000000, cur[i] & 0x00FFFFFF},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                shaderColor[i] = cur[i];
            }
            // each blob on its own slow Lissajous path
            float ph = i * 1.7f;
            float cx = w * (0.5f + 0.38f * (float) Math.sin(t * (0.9f + i * 0.17f) + ph));
            float cy = h * (0.42f + 0.34f * (float) Math.cos(t * (0.7f + i * 0.13f) + ph * 1.3f));
            float r = r0 * (0.85f + 0.12f * (float) Math.sin(t * 1.3f + i) + energy * 0.22f);
            m.setScale(r, r);
            m.postTranslate(cx, cy);
            shaders[i].setLocalMatrix(m);
            paint.setShader(shaders[i]);
            paint.setAlpha(i == 0 ? 255 : 200);
            c.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
        if ((playing && !still) || colorStart >= 0) postInvalidateDelayed(33);
    }

    static int blend(int a, int b, float f) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    // ------------------------------------------------------------------ bass «breathing»

    private boolean canListen() {
        return !still && session != 0 && Qvk3MusicSettings.prefs(getContext()).getBoolean(Qvk3MusicSettings.AMBIENT_REACT, true)
                && getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startVisualizer() {
        if (vis != null || !isAttachedToWindow() || !canListen()) return;
        try {
            vis = new Visualizer(session);
            vis.setCaptureSize(Math.min(256, Visualizer.getCaptureSizeRange()[1]));
            vis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] wave, int rate) {
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    // bins 1..6 of a 256-point FFT ≈ the bass; magnitude → 0..1
                    float sum = 0;
                    for (int i = 1; i <= 6 && 2 * i + 1 < fft.length; i++) {
                        float re = fft[2 * i], im = fft[2 * i + 1];
                        sum += (float) Math.sqrt(re * re + im * im);
                    }
                    energyTarget = Math.min(1f, sum / 6f / 90f);
                }
            }, Math.min(Visualizer.getMaxCaptureRate(), 15000), false, true);
            vis.setEnabled(true);
        } catch (Throwable e) {
            // no permission at runtime, effect busy or unsupported: plain drift
            stopVisualizer();
        }
    }

    private void stopVisualizer() {
        energyTarget = 0;
        if (vis == null) return;
        try {
            vis.setEnabled(false);
            vis.release();
        } catch (Throwable ignored) {
        }
        vis = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing) startVisualizer();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopVisualizer();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility != VISIBLE) stopVisualizer();
        else if (playing) {
            startVisualizer();
            invalidate();
        }
    }
}
