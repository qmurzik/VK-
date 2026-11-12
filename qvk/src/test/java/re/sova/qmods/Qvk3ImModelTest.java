package re.sova.qmods;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class Qvk3ImModelTest {
    static final String CONVS = "{\"count\":2,\"items\":["
            + "{\"conversation\":{\"peer\":{\"id\":2000000005,\"type\":\"chat\"},\"in_read_cmid\":90,\"out_read_cmid\":88,\"unread_count\":3,"
            + "\"sort_id\":{\"major_id\":16,\"minor_id\":120},\"chat_settings\":{\"title\":\"5 Г\",\"members_count\":24,\"photo\":{\"photo_100\":\"https://p/c.jpg\"}},"
            + "\"push_settings\":{\"disabled_forever\":true}},"
            + "\"last_message\":{\"id\":5001,\"conversation_message_id\":93,\"peer_id\":2000000005,\"from_id\":7,\"date\":1700000000,\"out\":0,\"text\":\"+\"}},"
            + "{\"conversation\":{\"peer\":{\"id\":7,\"type\":\"user\"},\"in_read_cmid\":10,\"out_read_cmid\":12,\"can_write\":{\"allowed\":false,\"reason\":902}},"
            + "\"last_message\":{\"id\":4999,\"conversation_message_id\":12,\"peer_id\":7,\"from_id\":1,\"date\":1699999000,\"out\":1,\"text\":\"\","
            + "\"attachments\":[{\"type\":\"photo\",\"photo\":{\"id\":1,\"owner_id\":1,\"sizes\":[{\"width\":130,\"height\":100,\"url\":\"s\"},{\"width\":807,\"height\":620,\"url\":\"m\"},{\"width\":1280,\"height\":984,\"url\":\"x\"}]}},"
            + "{\"type\":\"photo\",\"photo\":{\"id\":2,\"owner_id\":1,\"sizes\":[]}}]}}],"
            + "\"profiles\":[{\"id\":7,\"first_name\":\"Марина\",\"last_name\":\"К\",\"photo_100\":\"https://p/7.jpg\",\"online\":1,\"online_mobile\":1}],\"groups\":[]}";

    @Test public void conversationsAreParsed() throws Exception {
        List<Qvk3ImModel.Conversation> cs = Qvk3ImModel.conversations(new JSONObject(CONVS));
        assertEquals(2, cs.size());
        Qvk3ImModel.Conversation chat = cs.get(0);
        assertTrue(chat.chat());
        assertEquals("5 Г", chat.title);
        assertEquals(24, chat.members);
        assertTrue(chat.muted);
        assertTrue(chat.pinned);
        assertEquals(3, chat.unread);
        Qvk3ImModel.Conversation user = cs.get(1);
        assertEquals("Марина К", user.title);
        assertTrue(user.online);
        assertTrue(user.mobile);
        assertFalse(user.canWrite);
        assertEquals("Пользователь ограничил, кто может ему писать", user.cantWriteReason);
        assertTrue(user.outgoingRead());   // cmid 12 <= out_read_cmid 12
        assertEquals("2 фотографии", Qvk3ImModel.preview(user.last, null));
        assertEquals("m", user.last.attachments.get(0).preview);
        assertEquals("x", user.last.attachments.get(0).url);
        assertEquals("photo1_1", user.last.attachments.get(0).ref());
    }

    @Test public void messageWithReplyForwardReactionsAndVoice() throws Exception {
        JSONObject o = new JSONObject("{\"id\":10,\"conversation_message_id\":5,\"peer_id\":7,\"from_id\":7,\"date\":1,\"out\":0,\"text\":\"ok\",\"update_time\":2,"
                + "\"reply_message\":{\"id\":9,\"from_id\":1,\"text\":\"Пойдёшь?\"},"
                + "\"fwd_messages\":[{\"from_id\":3,\"text\":\"a\"},{\"from_id\":4,\"text\":\"b\"}],"
                + "\"reaction_id\":2,\"reactions\":[{\"reaction_id\":2,\"count\":3},{\"reaction_id\":5,\"count\":1}],"
                + "\"attachments\":[{\"type\":\"audio_message\",\"audio_message\":{\"id\":1,\"owner_id\":7,\"duration\":12,\"link_mp3\":\"https://a.mp3\",\"waveform\":[0,5,31],\"transcript\":\"привет\"}}]}");
        Qvk3ImModel.Message m = Qvk3ImModel.message(o);
        assertTrue(m.edited());
        assertEquals("Пойдёшь?", m.reply.text);
        assertEquals(2, m.forwards.size());
        assertEquals(2, m.reactions.size());
        assertTrue(m.reactions.get(0).mine);
        assertFalse(m.reactions.get(1).mine);
        Qvk3ImModel.Attachment a = m.attachments.get(0);
        assertEquals(12, a.duration);
        assertArrayEquals(new int[]{0, 5, 31}, a.waveform);
        m.text = "";
        assertEquals("Голосовое: привет", Qvk3ImModel.preview(m, null));
    }

    @Test public void serviceAndStickerAndMerge() throws Exception {
        Map<Long, Qvk3ImModel.Person> who = Qvk3ImModel.people(new JSONObject("{\"profiles\":[{\"id\":1,\"first_name\":\"Анна\",\"last_name\":\"А\"},{\"id\":2,\"first_name\":\"Олег\",\"last_name\":\"О\"}]}"));
        Qvk3ImModel.Message s = Qvk3ImModel.message(new JSONObject("{\"id\":3,\"from_id\":1,\"action\":{\"type\":\"chat_invite_user\",\"member_id\":2}}"));
        assertEquals("Анна А пригласил(а) Олег О", Qvk3ImModel.serviceText(s, who));
        Qvk3ImModel.Message st = Qvk3ImModel.message(new JSONObject("{\"id\":4,\"attachments\":[{\"type\":\"sticker\",\"sticker\":{\"sticker_id\":99,\"images\":[{\"width\":128,\"url\":\"a\"},{\"width\":256,\"url\":\"b\"},{\"width\":512,\"url\":\"c\"}]}}]}"));
        assertTrue(st.onlySticker());
        assertEquals("b", st.attachments.get(0).preview);
        Qvk3ImModel.Message pending = new Qvk3ImModel.Message();
        pending.date = 100;
        Qvk3ImModel.Message edited = Qvk3ImModel.message(new JSONObject("{\"id\":3,\"from_id\":1,\"text\":\"new\"}"));
        List<Qvk3ImModel.Message> merged = Qvk3ImModel.merge(Arrays.asList(st, s, pending), Arrays.asList(edited));
        assertEquals(3, merged.size());
        assertEquals(3, merged.get(0).id);
        assertEquals("new", merged.get(0).text);
        assertEquals(4, merged.get(1).id);
        assertEquals(0, merged.get(2).id);   // pending stays last
    }
}
