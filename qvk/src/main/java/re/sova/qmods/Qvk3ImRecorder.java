package re.sova.qmods;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Voice messages: a sheet with the timer; «Отправить» uploads the recording as an audio_message and sends it.
 * OGG/Opus on Android 10+ (what VK itself records), AAC before that.
 */
final class Qvk3ImRecorder {
    private Qvk3ImRecorder() {
    }

    static void open(Qvk3ImChatView chat) {
        Activity a = chat.a;
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7301);
            Qvk3Ui.toast(a, "Разрешите микрофон и откройте запись ещё раз");
            return;
        }
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        boolean ogg = Build.VERSION.SDK_INT >= 29;
        File out = new File(a.getCacheDir(), "qvk3_voice." + (ogg ? "ogg" : "m4a"));
        MediaRecorder rec;
        try {
            rec = new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            if (ogg) {
                rec.setOutputFormat(MediaRecorder.OutputFormat.OGG);
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            } else {
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }
            rec.setAudioSamplingRate(48000);
            rec.setAudioEncodingBitRate(32000);
            rec.setOutputFile(out.getAbsolutePath());
            rec.prepare();
            rec.start();
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось начать запись: " + Qvk3Jobs.message(e));
            return;
        }
        long started = System.currentTimeMillis();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Qvk3Ui.dp(a, 8), 0, Qvk3Ui.dp(a, 16));
        TextView timer = Qvk3Ui.text(a, "0:00", 40, p.text, true);
        col.addView(timer);
        TextView hint = Qvk3Ui.text(a, "Идёт запись", 14, 0xFFE64646, false);
        col.addView(hint);
        Handler h = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                int s = (int) ((System.currentTimeMillis() - started) / 1000);
                timer.setText(Qvk3ImUi.duration(s));
                if (s >= 600) { // VK limit-friendly: 10 minutes
                    hint.setText("Достигнута максимальная длина");
                    return;
                }
                h.postDelayed(this, 250);
            }
        };
        h.post(tick);
        Qvk3Ui.dialog(a).setTitle("Голосовое сообщение").setView(col).setCancelable(false)
                .setPositiveButton("Отправить", (d, w) -> {
                    done[0] = true;
                    if (!stop(rec) || System.currentTimeMillis() - started < 700) {
                        Qvk3Ui.toast(a, "Слишком короткая запись");
                        return;
                    }
                    Qvk3Ui.toast(a, "Отправляем голосовое…");
                    Qvk3Jobs.io(() -> {
                        try {
                            byte[] data = java.nio.file.Files.readAllBytes(out.toPath());
                            Qvk3ImModel.Attachment att = Qvk3ImAttach.doc(chat.peer, out.getName(), data, "audio_message");
                            Qvk3Jobs.main(() -> {
                                chat.attachUploaded(att.ref(), att);
                                chat.send();
                            });
                        } catch (Exception e) {
                            Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Голосовое не отправлено: " + Qvk3Jobs.message(e)));
                        } finally {
                            out.delete();
                        }
                    });
                })
                .setNegativeButton("Отменить", (d, w) -> {
                    done[0] = true;
                    stop(rec);
                    out.delete();
                }).show();
    }

    private static boolean stop(MediaRecorder r) {
        try {
            r.stop();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }
}
