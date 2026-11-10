package re.sova.qmods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal ID3v2.3 writer: title, artist, album and front cover, so saved tracks show correct names in any
 * player. An existing ID3v2 tag at the start of the file is replaced, the audio frames are copied as-is.
 */
public final class Qvk3Id3 {
    /** Returns a new file with the tag; the input file is left for the caller to delete. */
    public static File tag(File mp3, Qvk3Track t, byte[] jpeg) throws IOException {
        File out = new File(mp3.getPath() + ".tagged");
        try (InputStream in = new FileInputStream(mp3); OutputStream o = new FileOutputStream(out)) {
            byte[] head = new byte[10];
            int n = readFully(in, head);
            long skip = 0;
            if (n == 10 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                skip = syncsafe(head, 6); // header (10 bytes) already consumed
            } else {
                skip = -1;
            }
            o.write(build(t.title, t.artist, t.album, jpeg));
            if (skip < 0) {
                o.write(head, 0, n);
            } else {
                while (skip > 0) {
                    long s = in.skip(skip);
                    if (s <= 0) break;
                    skip -= s;
                }
            }
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) != -1) o.write(buf, 0, r);
        }
        return out;
    }

    static int readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int r = in.read(b, off, b.length - off);
            if (r < 0) break;
            off += r;
        }
        return off;
    }

    static long syncsafe(byte[] b, int off) {
        return ((b[off] & 0x7f) << 21) | ((b[off + 1] & 0x7f) << 14) | ((b[off + 2] & 0x7f) << 7) | (b[off + 3] & 0x7f);
    }

    public static byte[] build(String title, String artist, String album, byte[] jpeg) throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        text(frames, "TIT2", title);
        text(frames, "TPE1", artist);
        text(frames, "TALB", album);
        if (jpeg != null && jpeg.length > 0) {
            ByteArrayOutputStream p = new ByteArrayOutputStream();
            p.write(0); // ISO-8859-1 description
            p.write("image/jpeg".getBytes(StandardCharsets.ISO_8859_1));
            p.write(0);
            p.write(3); // front cover
            p.write(0); // empty description
            p.write(jpeg);
            frame(frames, "APIC", p.toByteArray());
        }
        byte[] body = frames.toByteArray();
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        tag.write(new byte[]{'I', 'D', '3', 3, 0, 0});
        int size = body.length;
        tag.write(new byte[]{(byte) ((size >> 21) & 0x7f), (byte) ((size >> 14) & 0x7f), (byte) ((size >> 7) & 0x7f), (byte) (size & 0x7f)});
        tag.write(body);
        return tag.toByteArray();
    }

    private static void text(ByteArrayOutputStream out, String id, String value) throws IOException {
        if (value == null || value.isEmpty()) return;
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(1); // UTF-16 with BOM
        p.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
        p.write(value.getBytes(StandardCharsets.UTF_16LE));
        frame(out, id, p.toByteArray());
    }

    private static void frame(ByteArrayOutputStream out, String id, byte[] payload) throws IOException {
        out.write(id.getBytes(StandardCharsets.ISO_8859_1));
        int n = payload.length;
        out.write(new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n, 0, 0});
        out.write(payload);
    }
}
