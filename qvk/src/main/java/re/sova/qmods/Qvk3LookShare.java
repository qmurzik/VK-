package re.sova.qmods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Share a VK look as one line of text: element rules, the floating bottom bar and the QVK 3.0 style. Someone else
 * pastes it and gets the same look; their own colours are only replaced if the shared look carries a style and they
 * agree. The code holds no account data, only appearance settings. Format: "QVKLOOK1:" + base64(JSON).
 */
final class Qvk3LookShare {
    static final String PREFIX = "QVKLOOK1:";

    private Qvk3LookShare() {
    }

    static String export(Context c) throws Exception {
        SharedPreferences p = Qvk3Skin.prefs(c);
        JSONObject o = new JSONObject();
        JSONArray rules = new JSONArray();
        for (Qvk3Skin.Rule r : Qvk3Skin.rules(c)) rules.put(r.json());
        o.put("rules", rules);
        o.put("dock", new JSONObject()
                .put("mode", p.getString(Qvk3Skin.DOCK, "off"))
                .put("radius", p.getInt(Qvk3Skin.DOCK_RADIUS, 24))
                .put("margin", p.getInt(Qvk3Skin.DOCK_MARGIN, 12))
                .put("alpha", p.getInt(Qvk3Skin.DOCK_ALPHA, 96)));
        if (p.contains(Qvk3Appearance.STYLE)) {
            o.put("style", p.getString(Qvk3Appearance.STYLE, "classic"));
            if ("custom".equals(p.getString(Qvk3Appearance.STYLE, ""))) o.put("tokens", new JSONObject(p.getString(Qvk3Appearance.TOKENS, "{}")));
        }
        return PREFIX + Base64.encodeToString(o.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    static JSONObject parse(String text) throws Exception {
        if (text == null) throw new IllegalArgumentException("Буфер пуст");
        int i = text.indexOf(PREFIX);
        if (i < 0) throw new IllegalArgumentException("В буфере нет кода оформления QVK");
        String b64 = text.substring(i + PREFIX.length()).trim().split("\\s")[0];
        if (b64.length() > 200_000) throw new IllegalArgumentException("Код слишком длинный");
        JSONObject o = new JSONObject(new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
        if (!o.has("rules") && !o.has("dock") && !o.has("style")) throw new IllegalArgumentException("Пустой код оформления");
        return o;
    }

    static String describe(JSONObject o) {
        StringBuilder s = new StringBuilder();
        JSONArray r = o.optJSONArray("rules");
        s.append("Правил для элементов: ").append(r == null ? 0 : r.length()).append('\n');
        JSONObject d = o.optJSONObject("dock");
        if (d != null) s.append("Нижняя панель: ").append("floating".equals(d.optString("mode")) ? "плавающая" : "стандартная").append('\n');
        if (o.has("style")) {
            Qvk3DesignTokens t = Qvk3DesignTokens.PRESETS.get(o.optString("style"));
            s.append("Стиль: ").append(t != null ? t.title : "свой").append('\n');
        }
        return s.toString();
    }

    static void apply(Activity a, JSONObject o, boolean withStyle) throws Exception {
        java.util.List<Qvk3Skin.Rule> list = new java.util.ArrayList<>();
        JSONArray r = o.optJSONArray("rules");
        for (int i = 0; r != null && i < r.length() && i < 300; i++) {
            Qvk3Skin.Rule x = Qvk3Skin.Rule.from(r.getJSONObject(i));
            if (x.id.matches("[A-Za-z0-9_]{1,80}")) list.add(x);
        }
        Qvk3Skin.saveRules(a, list);
        JSONObject d = o.optJSONObject("dock");
        if (d != null) {
            Qvk3Skin.prefs(a).edit()
                    .putString(Qvk3Skin.DOCK, "floating".equals(d.optString("mode")) ? "floating" : "off")
                    .putInt(Qvk3Skin.DOCK_RADIUS, Math.max(0, Math.min(40, d.optInt("radius", 24))))
                    .putInt(Qvk3Skin.DOCK_MARGIN, Math.max(0, Math.min(32, d.optInt("margin", 12))))
                    .putInt(Qvk3Skin.DOCK_ALPHA, Math.max(40, Math.min(100, d.optInt("alpha", 96)))).apply();
        }
        if (withStyle && o.has("style")) {
            String id = o.optString("style");
            Qvk3DesignTokens base = Qvk3DesignTokens.PRESETS.get(id);
            Qvk3DesignTokens t = "custom".equals(id) && o.has("tokens")
                    ? Qvk3DesignTokens.fromJson(o.getJSONObject("tokens"), Qvk3DesignTokens.PRESETS.get("classic"))
                    : base;
            if (t != null) Qvk3Appearance.apply(a, t, !Qvk3Appearance.hasOwnTheme(a));
        }
        Qvk3Skin.refresh();
    }

    // ------------------------------------------------------------------ UI

    static void share(Activity a) {
        try {
            String code = export(a);
            ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("QVK look", code));
            Qvk3Ui.toast(a, "Код оформления скопирован. Отправьте его другу: он вставит его в QVK");
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось: " + Qvk3Jobs.message(e));
        }
    }

    static void paste(Activity a) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm.getPrimaryClip();
            String text = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null ? clip.getItemAt(0).getText().toString() : null;
            JSONObject o = parse(text);
            android.app.AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle("Применить оформление?")
                    .setMessage(describe(o) + "\nТекущие правила элементов будут заменены. Отменить можно в «Недавних изменениях».")
                    .setNegativeButton("Отмена", null);
            if (o.has("style")) {
                b.setPositiveButton("Со стилем", (d, w) -> run(a, o, true));
                b.setNeutralButton("Только элементы", (d, w) -> run(a, o, false));
            } else {
                b.setPositiveButton("Применить", (d, w) -> run(a, o, false));
            }
            b.show();
        } catch (Exception e) {
            Qvk3Ui.toast(a, Qvk3Jobs.message(e));
        }
    }

    private static void run(Activity a, JSONObject o, boolean style) {
        try {
            apply(a, o, style);
            Qvk3Ui.toast(a, "Оформление применено");
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось применить: " + Qvk3Jobs.message(e));
        }
    }
}
