package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * VK messages API for the QVK messenger. Every call goes through VK 8.100's own authorised client
 * ({@link Qvk3MusicSource#response} → NetworkBridge), so no token is handled here and QVK's privacy switches
 * (нечиталка, непечаталка) keep working: NetworkBridge drops markAsRead / setActivity when the user turned them on.
 */
final class Qvk3ImApi {
    static final String FIELDS = "photo_100,online,online_mobile,last_seen,verified,first_name_dat";

    private Qvk3ImApi() {
    }

    static JSONObject call(String method, String... kv) throws Exception {
        return Qvk3MusicSource.response(method, Qvk3MusicSource.args(kv));
    }

    static JSONObject conversations(int offset, int count, String filter) throws Exception {
        return call("messages.getConversations", "offset", Integer.toString(offset), "count", Integer.toString(count),
                "extended", "1", "fields", FIELDS, "filter", filter);
    }

    static JSONObject conversationById(long peer) throws Exception {
        return call("messages.getConversationsById", "peer_ids", Long.toString(peer), "extended", "1", "fields", FIELDS);
    }

    /** History, newest first as VK returns it; startId = 0 → latest. */
    static JSONObject history(long peer, int offset, int count, long startId) throws Exception {
        return call("messages.getHistory", "peer_id", Long.toString(peer), "offset", Integer.toString(offset), "count", Integer.toString(count),
                "start_message_id", startId > 0 ? Long.toString(startId) : null, "extended", "1", "fields", FIELDS);
    }

    static JSONObject byIds(List<Long> ids) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Long id : ids) b.append(b.length() == 0 ? "" : ",").append(id);
        return call("messages.getById", "message_ids", b.toString(), "extended", "1", "fields", FIELDS);
    }

    /** Sends text with optional reply, forwards (from this chat) and attachments; returns the new message id. */
    static long send(long peer, long randomId, String text, long replyTo, List<Long> forward, List<String> attachments) throws Exception {
        JSONObject r = call("messages.send", "peer_id", Long.toString(peer), "random_id", Long.toString(randomId),
                "message", text, "reply_to", replyTo > 0 ? Long.toString(replyTo) : null,
                "forward_messages", forward == null || forward.isEmpty() ? null : joinIds(forward),
                "attachment", attachments == null || attachments.isEmpty() ? null : String.join(",", attachments));
        return number(r);
    }

    /** messages.send answers a bare number; {@link Qvk3MusicSource#response} keeps such answers wrapped. */
    static long number(JSONObject r) {
        Object v = r.opt("response");
        if (v instanceof Number) return ((Number) v).longValue();
        return r.optLong("message_id", r.optLong("id"));
    }

    static long sendSticker(long peer, long randomId, long stickerId, long replyTo) throws Exception {
        return number(call("messages.send", "peer_id", Long.toString(peer), "random_id", Long.toString(randomId),
                "sticker_id", Long.toString(stickerId), "reply_to", replyTo > 0 ? Long.toString(replyTo) : null));
    }

    /** Forwards messages from another chat (forward JSON keeps the source peer). */
    static long forward(long toPeer, long fromPeer, List<Long> ids, String comment) throws Exception {
        JSONObject f = new JSONObject().put("peer_id", fromPeer);
        JSONArray a = new JSONArray();
        for (Long id : ids) a.put(id);
        f.put("message_ids", a);
        JSONObject r = call("messages.send", "peer_id", Long.toString(toPeer), "random_id", Long.toString(System.nanoTime() & 0x7fffffffL),
                "message", comment, "forward", f.toString());
        return number(r);
    }

    static String joinIds(List<Long> ids) {
        StringBuilder b = new StringBuilder();
        for (Long id : ids) b.append(b.length() == 0 ? "" : ",").append(id);
        return b.toString();
    }

    static void edit(long peer, long id, String text, List<String> keepAttachments) throws Exception {
        call("messages.edit", "peer_id", Long.toString(peer), "message_id", Long.toString(id), "message", text,
                "attachment", keepAttachments == null || keepAttachments.isEmpty() ? null : String.join(",", keepAttachments),
                "keep_forward_messages", "1", "keep_snippets", "1");
    }

    static void delete(long peer, List<Long> ids, boolean forAll) throws Exception {
        call("messages.delete", "peer_id", Long.toString(peer), "message_ids", joinIds(ids), "delete_for_all", forAll ? "1" : "0");
    }

    static void markRead(long peer) throws Exception {
        call("messages.markAsRead", "peer_id", Long.toString(peer), "mark_conversation_as_read", "1");
    }

    static void markUnread(long peer) throws Exception {
        call("messages.markAsUnreadConversation", "peer_id", Long.toString(peer));
    }

    static void typing(long peer) {
        try {
            call("messages.setActivity", "peer_id", Long.toString(peer), "type", "typing");
        } catch (Exception ignored) {
        }
    }

    static void react(long peer, long cmid, int reaction) throws Exception {
        if (reaction == 0) call("messages.deleteReaction", "peer_id", Long.toString(peer), "cmid", Long.toString(cmid));
        else call("messages.sendReaction", "peer_id", Long.toString(peer), "cmid", Long.toString(cmid), "reaction_id", Integer.toString(reaction));
    }

    static void pin(long peer, long id) throws Exception {
        call("messages.pin", "peer_id", Long.toString(peer), "message_id", Long.toString(id));
    }

    static void unpin(long peer) throws Exception {
        call("messages.unpin", "peer_id", Long.toString(peer));
    }

    static void important(long id, boolean on) throws Exception {
        call("messages.markAsImportant", "message_ids", Long.toString(id), "important", on ? "1" : "0");
    }

    static void mute(long peer, boolean on) throws Exception {
        call("account.setSilenceMode", "peer_id", Long.toString(peer), "time", on ? "-1" : "0", "sound", on ? "0" : "1");
    }

    static void archive(long peer, boolean on) throws Exception {
        call(on ? "messages.archiveConversation" : "messages.unarchiveConversation", "peer_id", Long.toString(peer));
    }

    static void pinConversation(long peer, boolean on) throws Exception {
        call(on ? "messages.pinConversation" : "messages.unpinConversation", "peer_id", Long.toString(peer));
    }

    static void deleteConversation(long peer) throws Exception {
        call("messages.deleteConversation", "peer_id", Long.toString(peer));
    }

    static JSONObject search(String q, long peer, int count) throws Exception {
        return call("messages.search", "q", q, "peer_id", peer == 0 ? null : Long.toString(peer), "count", Integer.toString(count),
                "extended", "1", "fields", FIELDS);
    }

    // ------------------------------------------------------------------ chat info (messenger completion)

    /** Members of a group chat with their profiles (items: member_id, is_admin, is_owner, invited_by). */
    static JSONObject members(long peer) throws Exception {
        return call("messages.getConversationMembers", "peer_id", Long.toString(peer), "fields", FIELDS);
    }

    /** All attachments of one kind in a chat: photo, video, audio, doc, link, audio_message. */
    static JSONObject historyAttachments(long peer, String mediaType, String startFrom, int count) throws Exception {
        return call("messages.getHistoryAttachments", "peer_id", Long.toString(peer), "media_type", mediaType,
                "start_from", startFrom, "count", Integer.toString(count), "photo_sizes", "1", "extended", "1", "fields", FIELDS);
    }

    static String inviteLink(long peer, boolean reset) throws Exception {
        return call("messages.getInviteLink", "peer_id", Long.toString(peer), "reset", reset ? "1" : "0").optString("link");
    }

    static void renameChat(long peer, String title) throws Exception {
        call("messages.editChat", "chat_id", Long.toString(peer - 2_000_000_000L), "title", title);
    }

    /** Leaves a group chat (removes the current user from it). */
    static void leaveChat(long peer) throws Exception {
        long me = Qvk3MusicSource.account();
        call("messages.removeChatUser", "chat_id", Long.toString(peer - 2_000_000_000L), "member_id", Long.toString(me));
    }

    static void kick(long peer, long member) throws Exception {
        call("messages.removeChatUser", "chat_id", Long.toString(peer - 2_000_000_000L), "member_id", Long.toString(member));
    }

    /** Video for a message: video.save (private) → upload → "video{owner}_{id}". */
    static JSONObject videoSave(String name) throws Exception {
        return call("video.save", "name", name, "is_private", "1", "wallpost", "0");
    }

    static JSONObject longPollServer() throws Exception {
        return call("messages.getLongPollServer", "need_pts", "1", "lp_version", "19");
    }

    static JSONObject longPollHistory(long ts, long pts) throws Exception {
        return call("messages.getLongPollHistory", "ts", Long.toString(ts), "pts", Long.toString(pts), "lp_version", "19",
                "fields", FIELDS, "events_limit", "1000", "msgs_limit", "200", "extended", "1");
    }

    static Map<String, String> args(String... kv) {
        return Qvk3MusicSource.args(kv);
    }
}
