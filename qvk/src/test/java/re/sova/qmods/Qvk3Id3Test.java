package re.sova.qmods;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Qvk3Id3Test {
    static int indexOf(byte[] h, byte[] n) {
        outer:
        for (int i = 0; i + n.length <= h.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    static Qvk3Track t() {
        return new Qvk3Track(1, 2, "", "Кино", "Группа крови", "Альбом", 285, "", "", false, 0, "", 0);
    }

    @Test public void prependsTagAndKeepsAudio() throws Exception {
        File f = File.createTempFile("qvk_a", ".mp3");
        byte[] audio = {(byte) 0xFF, (byte) 0xFB, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        Files.write(f.toPath(), audio);
        File out = Qvk3Id3.tag(f, t(), new byte[]{1, 2, 3});
        byte[] all = Files.readAllBytes(out.toPath());
        assertEquals('I', all[0]);
        long size = Qvk3Id3.syncsafe(all, 6);
        byte[] rest = Arrays.copyOfRange(all, (int) size + 10, all.length);
        assertArrayEquals(audio, rest);
        assertTrue(indexOf(all, "Группа крови".getBytes("UTF-16LE")) > 0);
    }

    @Test public void replacesExistingTag() throws Exception {
        File f = File.createTempFile("qvk_b", ".mp3");
        byte[] old = Qvk3Id3.build("old", "old", "", null);
        byte[] audio = {(byte) 0xFF, (byte) 0xFB, 42};
        byte[] in = new byte[old.length + audio.length];
        System.arraycopy(old, 0, in, 0, old.length);
        System.arraycopy(audio, 0, in, old.length, audio.length);
        Files.write(f.toPath(), in);
        byte[] all = Files.readAllBytes(Qvk3Id3.tag(f, t(), null).toPath());
        long size = Qvk3Id3.syncsafe(all, 6);
        assertArrayEquals(audio, Arrays.copyOfRange(all, (int) size + 10, all.length));
        assertEquals(-1, indexOf(all, "old".getBytes("UTF-16LE")));
    }
}
