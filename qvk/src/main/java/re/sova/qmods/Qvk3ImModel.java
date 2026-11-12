package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model of the QVK messenger (pure Java, unit-tested): conversations, messages with every attachment kind VK
 * sends, replies, forwards, reactions and service actions, parsed from VK API answers (messages.* with extended=1).
 */
public final class Qvk3ImModel {
    private Qvk3ImModel() {
    }

    // ------------------------------------------------------------------ people

    public static final class Person {
        public final long id;          // user > 0, group < 0
        public final String name, first, photo;
        public final boolean online, mobile, verified;
        public final long lastSeen;

        Person(long id, String name, String first, String photo, boolean online, boolean mobile, boolean verified, long lastSeen) {
            this.id = id;
            this.name = name;
            this.first = first;
            this.photo = photo;
            this.online = online;
            this.mobile = mobile;
            this.verified = verified;
            this.lastSeen = lastSeen;
        }
    }

    /** profiles + groups of an extended answer, by id (groups negative). */
    public static Map<Long, Person> people(JSONObject r) {
        Map<Long, Person> m = new HashMap<>();
        if (r == null) return m;
        JSONArray p = r.optJSONArray("profiles");
        if (p != null) for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o == null) continue;
            String first = o.optString("first_name");
            JSONObject ls = o.optJSONObject("last_seen");
            m.put(o.optLong("id"), new Person(o.optLong("id"), (first + " " + o.optString("last_name")).trim(), first,
                    photo(o), o.optInt("online") == 1, o.optInt("online_mobile") == 1, o.optInt("verified") == 1, ls == null ? 0 : ls.optLong("time")));
        }
        JSONArray g = r.optJSONArray("groups");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o == null) continue;
            m.put(-o.optLong("id"), new Person(-o.optLong("id"), o.optString("name"), o.optString("name"), photo(o), false, false, o.optInt("verified") == 1, 0));
        }
        return m;
    }

    static String photo(JSONObject o) {
        for (String k : new String[]{"photo_100", "photo_200", "photo_50"}) if (!o.optString(k).isEmpty()) return o.optString(k);
        return "";
    }

    // ------------------------------------------------------------------ attachments

    public static final class Attachment {
        public final String type;          // photo, video, audio, doc, sticker, audio_message, link, wall, wall_reply, gift, poll, graffiti, story, call, market, money_transfer, geo, ...
        public String url = "";            // full image / file / link target
        public String preview = "";        // thumbnail
        public int width, height;          // of preview / photo
        public String title = "", subtitle = "", ext = "";
        public long size, ownerId, id;
        public int duration;               // seconds (video, audio, voice, call)
        public String accessKey = "";
        public String transcript = "";     // voice message text
        public int[] waveform;             // voice message 0..31
        public JSONObject raw;             // original, for the fallback «open in VK»

        Attachment(String type) {
            this.type = type;
        }

        /** VK attachment string for forwarding / re-sending ("photo123_456_key"). */
        public String ref() {
            if (ownerId == 0 && id == 0) return "";
            return type + ownerId + "_" + id + (accessKey.isEmpty() ? "" : "_" + accessKey);
        }

        public boolean visual() {
            return type.equals("photo") || type.equals("video") || type.equals("sticker") || type.equals("graffiti") || type.equals("story");
        }
    }

    /** Best size not wider than {@code max} (or the smallest above it) from a VK "sizes" array. */
    static JSONObject bestSize(JSONArray sizes, int max) {
        if (sizes == null) return null;
        JSONObject best = null, above = null;
        for (int i = 0; i < sizes.length(); i++) {
            JSONObject s = sizes.optJSONObject(i);
            if (s == null) continue;
            int w = s.optInt("width", s.optInt("w"));
            if (w <= max) {
                if (best == null || w > best.optInt("width", best.optInt("w"))) best = s;
            } else if (above == null || w < above.optInt("width", above.optInt("w"))) above = s;
        }
        return best != null ? best : above;
    }

    static String sizeUrl(JSONObject s) {
        return s == null ? "" : s.optString("url", s.optString("src"));
    }

    public static Attachment attachment(JSONObject a) {
        if (a == null) return null;
        String type = a.optString("type");
        JSONObject o = a.optJSONObject(type);
        Attachment x = new Attachment(type);
        x.raw = a;
        if (o == null) return x;
        x.ownerId = o.optLong("owner_id");
        x.id = o.optLong("id");
        x.accessKey = o.optString("access_key");
        switch (type) {
            case "photo": {
                JSONArray sizes = o.optJSONArray("sizes");
                JSONObject p = bestSize(sizes, 900), big = bestSize(sizes, 2560);
                x.preview = sizeUrl(p);
                x.url = sizeUrl(big);
                if (p != null) {
                    x.width = p.optInt("width");
                    x.height = p.optInt("height");
                }
                break;
            }
            case "video": {
                x.title = o.optString("title");
                x.duration = o.optInt("duration");
                JSONObject p = bestSize(o.optJSONArray("image"), 900);
                if (p == null) p = bestSize(o.optJSONArray("first_frame"), 900);
                x.preview = sizeUrl(p);
                if (p != null) {
                    x.width = p.optInt("width");
                    x.height = p.optInt("height");
                }
                x.url = "https://vk.com/video" + x.ownerId + "_" + x.id;
                break;
            }
            case "audio":
                x.title = o.optString("title");
                x.subtitle = o.optString("artist");
                x.duration = o.optInt("duration");
                x.url = o.optString("url");
                break;
            case "doc": {
                x.title = o.optString("title");
                x.ext = o.optString("ext");
                x.size = o.optLong("size");
                x.url = o.optString("url");
                JSONObject prev = o.optJSONObject("preview");
                JSONObject ph = prev == null ? null : prev.optJSONObject("photo");
                JSONObject p = bestSize(ph == null ? null : ph.optJSONArray("sizes"), 900);
                x.preview = sizeUrl(p);
                if (p != null) {
                    x.width = p.optInt("width");
                    x.height = p.optInt("height");
                }
                break;
            }
            case "sticker": {
                JSONObject p = bestSize(o.optJSONArray("images"), 352);
                x.preview = sizeUrl(p);
                x.width = x.height = 160;
                x.id = o.optLong("sticker_id");
                break;
            }
            case "audio_message": {
                x.duration = o.optInt("duration");
                x.url = o.optString("link_mp3", o.optString("link_ogg"));
                x.transcript = o.optString("transcript");
                JSONArray wf = o.optJSONArray("waveform");
                if (wf != null) {
                    x.waveform = new int[wf.length()];
                    for (int i = 0; i < wf.length(); i++) x.waveform[i] = wf.optInt(i);
                }
                break;
            }
            case "link": {
                x.url = o.optString("url");
                x.title = o.optString("title");
                x.subtitle = o.optString("caption", o.optString("description"));
                JSONObject ph = o.optJSONObject("photo");
                JSONObject p = bestSize(ph == null ? null : ph.optJSONArray("sizes"), 600);
                x.preview = sizeUrl(p);
                break;
            }
            case "wall":
                x.ownerId = o.optLong("owner_id", o.optLong("from_id"));
                x.title = "Запись на стене";
                x.subtitle = o.optString("text");
                x.url = "https://vk.com/wall" + x.ownerId + "_" + x.id;
                break;
            case "wall_reply":
                x.title = "Комментарий";
                x.subtitle = o.optString("text");
                break;
            case "gift": {
                x.title = "Подарок";
                x.preview = o.optString("thumb_256", o.optString("thumb_96"));
                x.width = x.height = 160;
                break;
            }
            case "poll":
                x.title = o.optString("question");
                x.subtitle = "Опрос · голосов: " + o.optInt("votes");
                break;
            case "graffiti":
                x.preview = o.optString("url");
                x.width = o.optInt("width");
                x.height = o.optInt("height");
                break;
            case "story": {
                x.title = "История";
                JSONObject ph = o.optJSONObject("photo");
                JSONObject p = bestSize(ph == null ? null : ph.optJSONArray("sizes"), 600);
                x.preview = sizeUrl(p);
                x.width = 9;
                x.height = 16;
                break;
            }
            case "call":
                x.title = o.optString("state").equals("reached") ? "Звонок" : "Пропущенный звонок";
                x.duration = o.optInt("duration");
                x.subtitle = o.optBoolean("video") ? "Видеозвонок" : "Аудиозвонок";
                break;
            case "market":
                x.title = o.optString("title");
                JSONObject price = o.optJSONObject("price");
                x.subtitle = price == null ? "Товар" : price.optString("text");
                x.preview = o.optString("thumb_photo");
                break;
            default:
                x.title = label(type);
        }
        return x;
    }

    /** Short Russian name of an attachment kind (chat list preview, fallbacks). */
    public static String label(String type) {
        switch (type) {
            case "photo": return "Фотография";
            case "video": return "Видео";
            case "audio": return "Аудиозапись";
            case "audio_message": return "Голосовое сообщение";
            case "doc": return "Файл";
            case "sticker": return "Стикер";
            case "wall": return "Запись со стены";
            case "wall_reply": return "Комментарий";
            case "link": return "Ссылка";
            case "gift": return "Подарок";
            case "poll": return "Опрос";
            case "graffiti": return "Граффити";
            case "story": return "История";
            case "call": return "Звонок";
            case "market": return "Товар";
            case "money_transfer": case "money_request": return "Денежный перевод";
            case "geo": return "Местоположение";
            case "artist": return "Исполнитель";
            case "playlist": return "Плейлист";
            case "podcast": return "Подкаст";
            case "article": return "Статья";
            default: return "Вложение";
        }
    }

    // ------------------------------------------------------------------ messages

    public static final class Reaction {
        public final int id, count;
        public final boolean mine;

        Reaction(int id, int count, boolean mine) {
            this.id = id;
            this.count = count;
            this.mine = mine;
        }
    }

    public static final class Message {
        public long id, cmid, peer, from, date, updateTime;
        public boolean out, important, pinned;
        public String text = "";
        public final List<Attachment> attachments = new ArrayList<>();
        public final List<Message> forwards = new ArrayList<>();
        public Message reply;
        public final List<Reaction> reactions = new ArrayList<>();
        public int myReaction;
        /** Service action: chat_create, chat_title_update, chat_invite_user, chat_kick_user, chat_pin_message, ... */
        public String action = "";
        public long actionMember;
        public String actionText = "";
        public boolean geo;
        /** Local state for messages being sent: 0 sent, 1 sending, 2 failed. */
        public int local;
        public long randomId;
        /** What a failed send needs for «повторить». */
        public java.util.List<Long> forwardsPending;
        public java.util.List<String> attachmentsPending;
        public long replyPending;

        public boolean service() {
            return !action.isEmpty();
        }

        public boolean edited() {
            return updateTime > 0;
        }

        public boolean onlySticker() {
            return text.isEmpty() && forwards.isEmpty() && reply == null && attachments.size() == 1
                    && (attachments.get(0).type.equals("sticker") || attachments.get(0).type.equals("graffiti"));
        }
    }

    public static Message message(JSONObject o) {
        if (o == null) return null;
        Message m = new Message();
        m.id = o.optLong("id");
        m.cmid = o.optLong("conversation_message_id");
        m.peer = o.optLong("peer_id");
        m.from = o.optLong("from_id");
        m.date = o.optLong("date");
        m.updateTime = o.optLong("update_time");
        m.out = o.optInt("out") == 1;
        m.important = o.optBoolean("important");
        m.text = o.optString("text");
        m.randomId = o.optLong("random_id");
        m.geo = o.has("geo");
        JSONArray atts = o.optJSONArray("attachments");
        if (atts != null) for (int i = 0; i < atts.length(); i++) {
            Attachment a = attachment(atts.optJSONObject(i));
            if (a != null) m.attachments.add(a);
        }
        if (m.geo) {
            Attachment g = new Attachment("geo");
            JSONObject geo = o.optJSONObject("geo");
            JSONObject c = geo == null ? null : geo.optJSONObject("coordinates");
            JSONObject place = geo == null ? null : geo.optJSONObject("place");
            g.title = place != null && !place.optString("title").isEmpty() ? place.optString("title") : "Местоположение";
            if (c != null) g.url = "geo:" + c.optDouble("latitude") + "," + c.optDouble("longitude");
            m.attachments.add(g);
        }
        JSONArray fwd = o.optJSONArray("fwd_messages");
        if (fwd != null) for (int i = 0; i < fwd.length(); i++) {
            Message f = message(fwd.optJSONObject(i));
            if (f != null) m.forwards.add(f);
        }
        m.reply = message(o.optJSONObject("reply_message"));
        JSONArray rs = o.optJSONArray("reactions");
        m.myReaction = o.optInt("reaction_id");
        if (rs != null) for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            int id = r.optInt("reaction_id");
            m.reactions.add(new Reaction(id, r.optInt("count"), id == m.myReaction && m.myReaction != 0));
        }
        JSONObject act = o.optJSONObject("action");
        if (act != null) {
            m.action = act.optString("type");
            m.actionMember = act.optLong("member_id");
            m.actionText = act.optString("text");
        }
        return m;
    }

    /** History answer → messages, oldest first. */
    public static List<Message> history(JSONObject r) {
        List<Message> out = new ArrayList<>();
        JSONArray items = r == null ? null : r.optJSONArray("items");
        if (items == null) return out;
        for (int i = items.length() - 1; i >= 0; i--) {
            Message m = message(items.optJSONObject(i));
            if (m != null) out.add(m);
        }
        return out;
    }

    /** Text of a service message, e.g. «Анна закрепила сообщение». */
    public static String serviceText(Message m, Map<Long, Person> who) {
        String actor = name(who, m.from), member = name(who, m.actionMember);
        switch (m.action) {
            case "chat_create": return actor + " создал(а) беседу «" + m.actionText + "»";
            case "chat_title_update": return actor + " переименовал(а) беседу в «" + m.actionText + "»";
            case "chat_photo_update": return actor + " обновил(а) фото беседы";
            case "chat_photo_remove": return actor + " удалил(а) фото беседы";
            case "chat_invite_user": return m.actionMember == m.from ? actor + " вернулся(-ась) в беседу" : actor + " пригласил(а) " + member;
            case "chat_kick_user": return m.actionMember == m.from ? actor + " вышел(-ла) из беседы" : actor + " исключил(а) " + member;
            case "chat_pin_message": return actor + " закрепил(а) сообщение";
            case "chat_unpin_message": return actor + " открепил(а) сообщение";
            case "chat_invite_user_by_link": return actor + " присоединился(-ась) по ссылке";
            case "chat_screenshot": return actor + " сделал(а) скриншот";
            case "conversation_style_update": return actor + " изменил(а) оформление чата";
            default: return actor + ": служебное сообщение";
        }
    }

    static String name(Map<Long, Person> who, long id) {
        Person p = who == null ? null : who.get(id);
        return p == null ? (id == 0 ? "" : "Пользователь") : p.name;
    }

    /** One line for the chat list / reply preview / notification. */
    public static String preview(Message m, Map<Long, Person> who) {
        if (m == null) return "";
        if (m.service()) return serviceText(m, who);
        if (!m.text.isEmpty()) return m.text.replace('\n', ' ');
        if (!m.attachments.isEmpty()) {
            Attachment a = m.attachments.get(0);
            if (a.type.equals("audio_message") && !a.transcript.isEmpty()) return "Голосовое: " + a.transcript;
            String l = label(a.type);
            return m.attachments.size() > 1 && a.type.equals("photo") ? m.attachments.size() + " фотографии" : l;
        }
        if (!m.forwards.isEmpty()) return m.forwards.size() == 1 ? "Пересланное сообщение" : m.forwards.size() + " пересланных сообщения";
        return "Сообщение";
    }

    // ------------------------------------------------------------------ conversations

    public static final class Conversation {
        public long peer;
        public String type = "user";       // user, chat, group, email
        public String title = "", photo = "";
        public int unread, members;
        public long inRead, outRead, lastCmid;
        public boolean muted, pinned, canWrite = true, online, mobile, verified, important;
        public String cantWriteReason = "";
        public long lastSeen;
        public Message last;
        public long sortId;
        public boolean archived;
        public Message pinnedMessage;

        public boolean chat() {
            return "chat".equals(type);
        }

        /** Was our last outgoing message read by the other side? */
        public boolean outgoingRead() {
            return last != null && last.out && (last.cmid > 0 ? last.cmid : last.id) <= outRead;
        }
    }

    public static Conversation conversation(JSONObject conv, Message last, Map<Long, Person> who) {
        Conversation c = new Conversation();
        JSONObject peer = conv.optJSONObject("peer");
        c.peer = peer == null ? 0 : peer.optLong("id");
        c.type = peer == null ? "user" : peer.optString("type", "user");
        c.unread = conv.optInt("unread_count");
        // read marks in conversation-message ids (cmid): the same ids Long Poll reports
        c.inRead = conv.optLong("in_read_cmid", conv.optLong("in_read"));
        c.outRead = conv.optLong("out_read_cmid", conv.optLong("out_read"));
        c.lastCmid = conv.optLong("last_conversation_message_id");
        c.important = conv.optBoolean("important");
        c.archived = conv.optBoolean("is_archived");
        JSONObject ps = conv.optJSONObject("push_settings");
        c.muted = ps != null && (ps.optBoolean("disabled_forever") || ps.optLong("disabled_until") > 0 || ps.optBoolean("no_sound"));
        JSONObject sort = conv.optJSONObject("sort_id");
        c.sortId = sort == null ? 0 : sort.optLong("major_id") * 1_000_000_000L + sort.optLong("minor_id");
        c.pinned = sort != null && sort.optLong("major_id") > 0;
        JSONObject cw = conv.optJSONObject("can_write");
        c.canWrite = cw == null || cw.optBoolean("allowed", true);
        if (!c.canWrite) c.cantWriteReason = cantWrite(cw.optInt("reason"));
        JSONObject cs = conv.optJSONObject("chat_settings");
        if (cs != null) {
            c.title = cs.optString("title");
            c.members = cs.optInt("members_count");
            c.pinnedMessage = message(cs.optJSONObject("pinned_message"));
            JSONObject ph = cs.optJSONObject("photo");
            if (ph != null) c.photo = photo(ph);
        } else {
            Person p = who == null ? null : who.get(c.peer);
            if (p != null) {
                c.title = p.name;
                c.photo = p.photo;
                c.online = p.online;
                c.mobile = p.mobile;
                c.verified = p.verified;
                c.lastSeen = p.lastSeen;
            }
        }
        if (c.title.isEmpty()) c.title = c.peer > 2_000_000_000L ? "Беседа" : "Диалог";
        c.last = last;
        return c;
    }

    static String cantWrite(int reason) {
        switch (reason) {
            case 18: return "Пользователь удалён или заблокирован";
            case 900: return "Вы в чёрном списке у этого пользователя";
            case 901: return "Сообщество запретило сообщения";
            case 902: return "Пользователь ограничил, кто может ему писать";
            case 917: return "Нет доступа к беседе";
            case 945: return "Беседа закрыта";
            default: return "Писать в этот чат нельзя";
        }
    }

    /** messages.getConversations answer → conversations in VK's order. */
    public static List<Conversation> conversations(JSONObject r) {
        List<Conversation> out = new ArrayList<>();
        if (r == null) return out;
        Map<Long, Person> who = people(r);
        JSONArray items = r.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null || it.optJSONObject("conversation") == null) continue;
            out.add(conversation(it.optJSONObject("conversation"), message(it.optJSONObject("last_message")), who));
        }
        return out;
    }

    /** Merges messages into an ordered list (by cmid/id), replacing ones with the same id. Pure helper for the store. */
    public static List<Message> merge(List<Message> base, List<Message> add) {
        Map<Long, Message> byId = new HashMap<>();
        List<Message> out = new ArrayList<>();
        for (Message m : base) if (m.id != 0) byId.put(m.id, m); else out.add(m);
        for (Message m : add) if (m.id != 0) byId.put(m.id, m);
        out.addAll(byId.values());
        // pending local messages (id 0) go last
        Collections.sort(out, (x, y) -> x.id == 0 && y.id == 0 ? Long.compare(x.date, y.date)
                : x.id == 0 ? 1 : y.id == 0 ? -1 : Long.compare(x.id, y.id));
        return out;
    }
}
