package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IDEA-0002: open "Медиа стены" for a community without copying its link into settings. In the community press
 * ⋮ → «Поделиться» → «Ещё» (system sheet) → «Медиа стены (QVK)». The shared text is parsed for a VK link or id,
 * resolved through VK's own API client, and QVK 2.x's wall gallery opens for that wall.
 */
public final class Qvk3WallShare extends Activity {
    private static final Pattern LINK = Pattern.compile("(?:https?://)?(?:m\\.)?vk\\.(?:com|ru)/([A-Za-z0-9_.\\-]{2,64})");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String text = getIntent() == null ? null : getIntent().getStringExtra(Intent.EXTRA_TEXT);
        String name = screenName(text);
        if (name == null) {
            Qvk3Ui.toast(this, "В том, чем поделились, нет ссылки ВКонтакте");
            finish();
            return;
        }
        long direct = direct(name);
        if (direct != 0) {
            open(direct);
            return;
        }
        Qvk3Jobs.io(() -> {
            long owner = 0;
            try {
                JSONObject r = Qvk3MusicSource.response("utils.resolveScreenName", Qvk3MusicSource.args("screen_name", name));
                long id = r.optLong("object_id");
                String type = r.optString("type");
                if ("user".equals(type)) owner = id;
                else if ("group".equals(type) || "page".equals(type) || "event".equals(type)) owner = -id;
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.wallshare.resolve", e);
            }
            final long o = owner;
            Qvk3Jobs.main(() -> {
                if (o == 0) {
                    Qvk3Ui.toast(this, "Не удалось определить страницу по ссылке");
                    finish();
                } else {
                    open(o);
                }
            });
        });
    }

    private void open(long owner) {
        try {
            WallGallery.open(this, owner);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.wallshare.open", t);
            Qvk3Ui.toast(this, "Медиа стены недоступны: " + Qvk3Jobs.message(t));
        }
        finish();
    }

    /** "vk.com/club123" → "club123"; also a bare screen name or numeric id. */
    static String screenName(String text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        if (m.find()) {
            String n = m.group(1);
            if (n.startsWith("wall-") || n.startsWith("wall")) {
                Matcher w = Pattern.compile("wall(-?[0-9]+)_").matcher(n);
                if (w.find()) return w.group(1);
            }
            return n;
        }
        String t = text.trim();
        return t.matches("-?[0-9]{1,10}|[A-Za-z0-9_.]{2,64}") ? t : null;
    }

    /** Ids that need no network: club123 / public123 / event123 → -123, id123 → 123, plain numbers as given. */
    static long direct(String name) {
        try {
            if (name.matches("-?[0-9]{1,10}")) return Long.parseLong(name);
            Matcher m = Pattern.compile("(id|club|public|event)([0-9]{1,10})").matcher(name);
            if (m.matches()) {
                long id = Long.parseLong(m.group(2));
                return m.group(1).equals("id") ? id : -id;
            }
        } catch (NumberFormatException ignored) {
            // not an id
        }
        return 0;
    }
}
