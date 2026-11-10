package re.sova.qmods;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class Qvk3SettingsIndexTest {
    static final String FIXTURE = "[{\"name\":\"Сообщения\",\"items\":["
            + "{\"key\":\"dnrGlobal\",\"title\":\"Нечиталка везде\",\"status\":\"connected\",\"switch\":true,\"summary\":\"Не отмечать прочитанным\"},"
            + "{\"key\":\"fastReplies\",\"title\":\"Быстрые ответы\",\"status\":\"connected\",\"switch\":false,\"action\":\"replies\"},"
            + "{\"key\":\"gone\",\"title\":\"Старое\",\"status\":\"unavailable\",\"switch\":true}]},"
            + "{\"name\":\"Музыка\",\"items\":[{\"key\":\"autoCache\",\"title\":\"Кэшировать музыку\",\"status\":\"partial\",\"switch\":true}]}]";

    @Test public void catalogEntriesAreTypedAndUnavailableSkipped() throws Exception {
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        idx.addCatalog(new JSONArray(FIXTURE));
        assertEquals(3, idx.all().size());
        assertEquals(Qvk3SettingsIndex.SWITCH, idx.get("dnrGlobal").type);
        assertEquals("dnrGlobal", idx.get("dnrGlobal").key);
        assertEquals(Qvk3SettingsIndex.ACTION, idx.get("fastReplies").type);
        assertEquals("replies", idx.get("fastReplies").target);
        assertNull(idx.get("gone"));
        assertTrue(idx.get("autoCache").summary.contains("частично"));
        assertEquals(5, idx.get("autoCache").section);
        assertEquals(3, idx.get("dnrGlobal").section);
    }

    @Test public void searchMatchesPrefixesAcrossWordsAndRanksTitles() throws Exception {
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        idx.addCatalog(new JSONArray(FIXTURE));
        idx.addScreen("x", "Сообщения сообществ", "Нечиталка не действует", 3);
        List<Qvk3SettingsIndex.Item> r = idx.search("нечит", 10);
        assertEquals("dnrGlobal", r.get(0).id);
        assertEquals(2, r.size());
        assertTrue(idx.search("быстр отв", 10).get(0).id.equals("fastReplies"));
        assertTrue(idx.search("ЁЛКИ несуществующее", 10).isEmpty());
        assertEquals(1, idx.search("кэшировать", 10).size());
    }

    @Test public void duplicateIdsIgnored() {
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        idx.addScreen("a", "A", "", 0);
        idx.addScreen("a", "A2", "", 1);
        assertEquals(1, idx.all().size());
    }

    /** Runs against the real QVK 2.6.18 catalog when the build provides it. */
    @Test public void realCatalogIsFullyMapped() throws Exception {
        String path = System.getProperty("qvk.features");
        Assume.assumeTrue(path != null && new File(path).isFile());
        JSONArray cat = new JSONArray(new String(Files.readAllBytes(new File(path).toPath()), "UTF-8"));
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        idx.addCatalog(cat);
        assertTrue("catalog size " + idx.all().size(), idx.all().size() >= 150);
        int[] perSection = new int[Qvk3SettingsIndex.SECTIONS.length];
        for (Qvk3SettingsIndex.Item i : idx.all()) perSection[i.section]++;
        for (int s : new int[]{0, 1, 2, 3, 4, 5, 6, 7}) assertTrue(Qvk3SettingsIndex.SECTIONS[s], perSection[s] > 0);
        assertFalse(idx.search("PIN", 5).isEmpty());
        assertFalse(idx.search("стикеры telegram", 5).isEmpty());
    }
}
