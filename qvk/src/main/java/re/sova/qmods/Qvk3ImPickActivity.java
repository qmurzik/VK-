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

    static void pick(Activity from, String mime, Result r) {
        pending = r;
        from.startActivity(new Intent(from, Qvk3ImPickActivity.class).putExtra(MIME, mime));
        from.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(getIntent().getStringExtra(MIME)).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "Выберите файлы"), 1);
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Нет приложения для выбора файлов");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
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
