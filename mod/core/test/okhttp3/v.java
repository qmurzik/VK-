package okhttp3;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
/** ResponseBody */
public abstract class v {
    public abstract m contentType();
    public abstract byte[] raw();
    public final String string() throws IOException { return new String(raw(), StandardCharsets.UTF_8); }
    public final byte[] bytes() throws IOException { return raw(); }
    public static final v create(String s, m mt) { return make(s.getBytes(StandardCharsets.UTF_8), mt); }
    public static final v create(byte[] b, m mt) { return make(b, mt); }
    public static final v create(m mt, String s) { return create(s, mt); }
    private static v make(final byte[] b, final m mt) { return new v() { public m contentType() { return mt; } public byte[] raw() { return b; } }; }
}
