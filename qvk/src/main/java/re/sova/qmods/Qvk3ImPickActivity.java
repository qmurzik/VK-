package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Invisible helper: opens the system picker and hands the chosen files to the messenger. Needed because the chat may
 * be hosted inside VK's own fragment, whose activity results QVK does not receive.
 */
public final class Qvk3ImPickActivity extends Activity {
    interface Result {
        void picked(List<Uri> uris);
    }

    static Result pending;
    static final String MIME = "mime";
    /** Special "mime": take a photo with the camera app (saved to Pictures/QVK, then sent like a gallery photo). */
    static final String CAMERA = "qvk3/camera";
    /** «Кружок»: a short front-camera video from the camera app. */
    static final String CIRCLE = "qvk3/circle";
    private Uri shot;

    static void pick(Activity from, String mime, Result r) {
        pending = r;
        from.startActivity(new Intent(from, Qvk3ImPickActivity.class).putExtra(MIME, mime));
        from.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        if (CAMERA.equals(getIntent().getStringExtra(MIME)) || CIRCLE.equals(getIntent().getStringExtra(MIME))) {
            // VK declares CAMERA: then ACTION_IMAGE_CAPTURE needs it granted, so ask first
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 2);
            } else {
                camera();
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(getIntent().getStringExtra(MIME)).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "Выберите файлы"), 1);
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Нет приложения для выбора файлов");
            finish();
        }
    }

    private void camera() {
        if (CIRCLE.equals(getIntent().getStringExtra(MIME))) {
            try {
                Intent i = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
                        .putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 60)
                        .putExtra(android.provider.MediaStore.EXTRA_VIDEO_QUALITY, 1)
                        .putExtra(android.provider.MediaStore.EXTRA_SIZE_LIMIT, 55L << 20)
                        // front camera: a common hint understood by most camera apps
                        .putExtra("android.intent.extras.CAMERA_FACING", 1)
                        .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                        .putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                startActivityForResult(i, 4);
            } catch (Exception e) {
                Qvk3Ui.toast(this, "Камера недоступна: " + Qvk3Jobs.message(e));
                finish();
            }
            return;
        }
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "QVK_" + System.currentTimeMillis() + ".jpg");
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (android.os.Build.VERSION.SDK_INT >= 29) v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QVK");
            shot = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, shot)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, 3);
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Камера недоступна: " + Qvk3Jobs.message(e));
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) camera();
        else {
            Qvk3Ui.toast(this, "Без доступа к камере снять фото нельзя");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        if (req == 4) {
            Result r = pending;
            pending = null;
            if (code == RESULT_OK && data != null && data.getData() != null && r != null) {
                List<Uri> one = new ArrayList<>();
                one.add(data.getData());
                r.picked(one);
            }
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (req == 3) {
            Result r = pending;
            pending = null;
            if (code == RESULT_OK && shot != null && r != null) {
                List<Uri> one = new ArrayList<>();
                one.add(shot);
                r.picked(one);
            } else if (shot != null) {
                try {
                    getContentResolver().delete(shot, null, null);   // cancelled: no empty file left in the gallery
                } catch (Exception ignored) {
                }
            }
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        List<Uri> out = new ArrayList<>();
        if (code == RESULT_OK && data != null) {
            if (data.getClipData() != null) for (int k = 0; k < data.getClipData().getItemCount() && k < 10; k++) out.add(data.getClipData().getItemAt(k).getUri());
            else if (data.getData() != null) out.add(data.getData());
        }
        Result r = pending;
        pending = null;
        if (r != null && !out.isEmpty()) r.picked(out);
        finish();
        overridePendingTransition(0, 0);
    }
}
