package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Messages data source of the QVK shell. Uses VK's own authorised API client (NetworkBridge), so QVK's
 * privacy rules (нечиталка, «печатает») keep applying and no token is handled here.
 */
public final class Qvk3Messages {
    public static final class Chat {
        public long peer;
        public String title = "", photo = "", preview = "";
        public long date;
        public int unread;
        public boolean out, online, group;
    }

    public static final class Msg {
        public long id, from, date;
        public boolean out;
        public String text = "", photo = "", sender = "";
        public int photoW, photoH;
    }

    static String attachLabel(JSONArray atts) {
        if (atts == null || atts.length() == 0) return "";
        switch (atts.optJSONObject(0) == null ? "" : atts.optJSONObject(0).optString("type")) {
            case "photo": return "Фотография";
            case "video": return "Видео";
            case "audio": return "Аудиозапись";
            case "audio_message": return "Голосовое сообщение";
            case "doc": return "Файл";
            case "sticker": return "Стикер";
            case "wall": return "Запись со стены";
            case "link": return "Ссылка";
            case "gift": return "Подарок";
            case "call": return "Звонок";
            default: return "Вложение";
        }
    }

    static Map<Long, String[]> people(JSONObject r) {
        Map<Long, String[]> m = new HashMap<>();
        JSONArray p = r.optJSONArray("profiles");
        if (p != null) for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null) m.put(o.optLong("id"), new String[]{(o.optString("first_name") + " " + o.optString("last_name")).trim(), o.optString("photo_100"), o.optInt("online") == 1 ? "1" : ""});
        }
        JSONArray g = r.optJSONArray("groups");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o != null) m.put(-o.optLong("id"), new String[]{o.optString("name"), o.optString("photo_100"), ""});
        }
        return m;
    }

    public static List<Chat> parseChats(JSONObject r) {
        Map<Long, String[]> who = people(r);
        List<Chat> out = new ArrayList<>();
        JSONArray items = r.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            JSONObject conv = it.optJSONObject("conversation"), last = it.optJSONObject("last_message");
            if (conv == null) continue;
            Chat c = new Chat();
            JSONObject peer = conv.optJSONObject("peer");
            c.peer = peer == null ? 0 : peer.optLong("id");
            c.unread = conv.optInt("unread_count");
            JSONObject cs = conv.optJSONObject("chat_settings");
            if (cs != null) {
                c.group = true;
                c.title = cs.optString("title");
                JSONObject ph = cs.optJSONObject("photo");
                if (ph != null) c.photo = ph.optString("photo_100");
            } else {
                String[] w = who.get(c.peer);
                if (w != null) {
                    c.title = w[0];
                    c.photo = w[1];
                    c.online = !w[2].isEmpty();
                }
            }
            if (c.title.isEmpty()) c.title = "Чат " + c.peer;
            if (last != null) {
                c.date = last.optLong("date");
                c.out = last.optInt("out") == 1;
                String t = last.optString("text");
                c.preview = t.isEmpty() ? attachLabel(last.optJSONArray("attachments")) : t.replace('\n', ' ');
                if (c.preview.isEmpty() && last.has("action")) c.preview = "Служебное сообщение";
                if (c.group && !c.out) {
                    String[] w = who.get(last.optLong("from_id"));
                    if (w != null && !w[0].isEmpty()) c.preview = w[0].split(" ")[0] + ": " + c.preview;
                }
            }
            out.add(c);
        }
        return out;
    }

    /** Oldest first. */
    public static List<Msg> parseHistory(JSONObject r) {
        Map<Long, String[]> who = people(r);
        List<Msg> out = new ArrayList<>();
        JSONArray items = r.optJSONArray("items");
        if (items == null) return out;
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            Msg m = new Msg();
            m.id = o.optLong("id");
            m.from = o.optLong("from_id");
            m.date = o.optLong("date");
            m.out = o.optInt("out") == 1;
            m.text = o.optString("text");
            String[] w = who.get(m.from);
            if (w != null) m.sender = w[0];
            JSONArray atts = o.optJSONArray("attachments");
            if (atts != null && atts.length() > 0) {
                JSONObject a = atts.optJSONObject(0);
                JSONObject ph = a == null ? null : a.optJSONObject("photo");
                JSONArray sizes = ph == null ? null : ph.optJSONArray("sizes");
                if (sizes != null) {
                    int best = -1;
                    for (int k = 0; k < sizes.length(); k++) {
                        JSONObject s = sizes.optJSONObject(k);
                        if (s != null && s.optInt("width") <= 900 && s.optInt("width") > best) {
                            best = s.optInt("width");
                            m.photo = s.optString("url");
                            m.photoW = s.optInt("width");
                            m.photoH = s.optInt("height");
                        }
                    }
                }
                if (m.photo.isEmpty() && m.text.isEmpty()) m.text = "[" + attachLabel(atts) + "]";
                else if (m.photo.isEmpty()) m.text = m.text + "\n[" + attachLabel(atts) + "]";
            }
            if (m.text.isEmpty() && m.photo.isEmpty()) m.text = o.has("action") ? "[Служебное сообщение]" : "[Сообщение без текста]";
            out.add(m);
        }
        return out;
    }

    public static List<Chat> chats(int offset) throws Exception {
        return parseChats(Qvk3MusicSource.response("messages.getConversations",
                Qvk3MusicSource.args("count", "40", "offset", Integer.toString(offset), "extended", "1", "fields", "photo_100,online")));
    }

    public static List<Msg> history(long peer, int offset) throws Exception {
        return parseHistory(Qvk3MusicSource.response("messages.getHistory",
                Qvk3MusicSource.args("peer_id", Long.toString(peer), "count", "50", "offset", Integer.toString(offset), "extended", "1", "fields", "photo_100")));
    }

    public static void send(long peer, String text) throws Exception {
        Qvk3MusicSource.response("messages.send", Qvk3MusicSource.args("peer_id", Long.toString(peer),
                "random_id", Long.toString(System.nanoTime() & 0x7fffffffL), "message", text));
    }

    /** Subject to QVK's «нечиталка»: NetworkBridge drops this call when the user enabled it. */
    public static void markRead(long peer) {
        try {
            Qvk3MusicSource.response("messages.markAsRead", Qvk3MusicSource.args("peer_id", Long.toString(peer)));
        } catch (Exception ignored) {
        }
    }

    public static String time(long unix) {
        java.util.Calendar c = java.util.Calendar.getInstance(), now = java.util.Calendar.getInstance();
        c.setTimeInMillis(unix * 1000);
        boolean today = c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        return new java.text.SimpleDateFormat(today ? "HH:mm" : "dd.MM.yy", java.util.Locale.ROOT).format(c.getTime());
    }
}
