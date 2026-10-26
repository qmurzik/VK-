package com.vkmod.net;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal MessagePack <-> org.json converter. VK negotiates application/x-msgpack for feed responses.
 * Anything that cannot be represented losslessly in JSON (bin, ext, non-string keys, uint64 overflow)
 * makes the conversion return null, so the caller keeps the original bytes untouched.
 */
public final class Msgpack {
    private Msgpack() {}

    private static final class Unsupported extends RuntimeException {
        Unsupported(String m) { super(m, null, false, false); }
    }

    public static String toJson(byte[] data) {
        try {
            Reader r = new Reader(data);
            Object v = r.read(0);
            if (r.pos != data.length) return null;
            if (!(v instanceof JSONObject) && !(v instanceof JSONArray)) return null;
            return v.toString();
        } catch (Unsupported | IndexOutOfBoundsException | org.json.JSONException e) {
            return null;
        }
    }

    public static byte[] fromJson(String json) {
        try {
            Object root = new JSONTokener(json).nextValue();
            if (!(root instanceof JSONObject) && !(root instanceof JSONArray)) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream(json.length());
            write(out, root);
            return out.toByteArray();
        } catch (Unsupported | org.json.JSONException e) {
            return null;
        }
    }

    // ---- reader ----
    private static final class Reader {
        final byte[] b; int pos;
        Reader(byte[] b) { this.b = b; }

        int u8() { return b[pos++] & 0xFF; }
        int u16() { int v = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF); pos += 2; return v; }
        long u32() { long v = ((long) (b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16) | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF); pos += 4; return v; }
        long i64() { long v = 0; for (int i = 0; i < 8; i++) v = (v << 8) | (b[pos + i] & 0xFF); pos += 8; return v; }

        String str(int n) {
            if (n < 0 || pos + n > b.length) throw new Unsupported("str len");
            String s = new String(b, pos, n, StandardCharsets.UTF_8);
            pos += n;
            return s;
        }

        Object read(int depth) throws org.json.JSONException {
            if (depth > 64) throw new Unsupported("depth");
            int t = u8();
            if (t <= 0x7f) return t;
            if (t >= 0xe0) return (int) (byte) t;
            if ((t & 0xe0) == 0xa0) return str(t & 0x1f);
            if ((t & 0xf0) == 0x90) return array(t & 0x0f, depth);
            if ((t & 0xf0) == 0x80) return map(t & 0x0f, depth);
            switch (t) {
                case 0xc0: return JSONObject.NULL;
                case 0xc2: return Boolean.FALSE;
                case 0xc3: return Boolean.TRUE;
                case 0xca: return (double) Float.intBitsToFloat((int) u32());
                case 0xcb: return Double.longBitsToDouble(i64());
                case 0xcc: return u8();
                case 0xcd: return u16();
                case 0xce: { long v = u32(); return v > Integer.MAX_VALUE ? (Object) v : (Object) (int) v; }
                case 0xcf: { long v = i64(); if (v < 0) throw new Unsupported("uint64"); return v; }
                case 0xd0: return (int) (byte) u8();
                case 0xd1: return (int) (short) u16();
                case 0xd2: { long v = (int) u32(); return (int) v; }
                case 0xd3: return i64();
                case 0xd9: return str(u8());
                case 0xda: return str(u16());
                case 0xdb: return str((int) u32());
                case 0xdc: return array(u16(), depth);
                case 0xdd: return array((int) u32(), depth);
                case 0xde: return map(u16(), depth);
                case 0xdf: return map((int) u32(), depth);
                default: throw new Unsupported("type 0x" + Integer.toHexString(t));
            }
        }

        JSONArray array(int n, int depth) throws org.json.JSONException {
            JSONArray a = new JSONArray();
            for (int i = 0; i < n; i++) a.put(read(depth + 1));
            return a;
        }

        JSONObject map(int n, int depth) throws org.json.JSONException {
            JSONObject o = new JSONObject();
            for (int i = 0; i < n; i++) {
                Object k = read(depth + 1);
                if (!(k instanceof String)) throw new Unsupported("non-string key");
                o.put((String) k, read(depth + 1));
            }
            return o;
        }
    }

    // ---- writer ----
    private static void w16(ByteArrayOutputStream o, int v) { o.write(v >> 8); o.write(v); }
    private static void w32(ByteArrayOutputStream o, long v) { o.write((int) (v >> 24)); o.write((int) (v >> 16)); o.write((int) (v >> 8)); o.write((int) v); }
    private static void w64(ByteArrayOutputStream o, long v) { for (int s = 56; s >= 0; s -= 8) o.write((int) (v >> s)); }

    private static void write(ByteArrayOutputStream o, Object v) throws org.json.JSONException {
        if (v == null || v == JSONObject.NULL) { o.write(0xc0); return; }
        if (v instanceof Boolean) { o.write(((Boolean) v) ? 0xc3 : 0xc2); return; }
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) { writeLong(o, ((Number) v).longValue()); return; }
        if (v instanceof Long) { writeLong(o, (Long) v); return; }
        if (v instanceof Double || v instanceof Float) { o.write(0xcb); w64(o, Double.doubleToLongBits(((Number) v).doubleValue())); return; }
        if (v instanceof Number) { // BigDecimal/BigInteger from non-Android org.json builds
            Number n = (Number) v;
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 9.0e18 && !(v instanceof java.math.BigDecimal && ((java.math.BigDecimal) v).scale() > 0)) writeLong(o, n.longValue());
            else { o.write(0xcb); w64(o, Double.doubleToLongBits(d)); }
            return;
        }
        if (v instanceof String) { writeStr(o, (String) v); return; }
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v; int n = a.length();
            if (n < 16) o.write(0x90 | n); else if (n < 65536) { o.write(0xdc); w16(o, n); } else { o.write(0xdd); w32(o, n); }
            for (int i = 0; i < n; i++) write(o, a.get(i));
            return;
        }
        if (v instanceof JSONObject) {
            JSONObject m = (JSONObject) v; int n = m.length();
            if (n < 16) o.write(0x80 | n); else if (n < 65536) { o.write(0xde); w16(o, n); } else { o.write(0xdf); w32(o, n); }
            java.util.Iterator<String> it = m.keys();
            while (it.hasNext()) { String k = it.next(); writeStr(o, k); write(o, m.get(k)); }
            return;
        }
        throw new Unsupported("value " + v.getClass());
    }

    private static void writeLong(ByteArrayOutputStream o, long v) {
        if (v >= 0) {
            if (v < 128) o.write((int) v);
            else if (v < 256) { o.write(0xcc); o.write((int) v); }
            else if (v < 65536) { o.write(0xcd); w16(o, (int) v); }
            else if (v < 4294967296L) { o.write(0xce); w32(o, v); }
            else { o.write(0xd3); w64(o, v); }
        } else {
            if (v >= -32) o.write((int) v & 0xff);
            else if (v >= -128) { o.write(0xd0); o.write((int) v); }
            else if (v >= -32768) { o.write(0xd1); w16(o, (int) v); }
            else if (v >= Integer.MIN_VALUE) { o.write(0xd2); w32(o, v); }
            else { o.write(0xd3); w64(o, v); }
        }
    }

    private static void writeStr(ByteArrayOutputStream o, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        int n = d.length;
        if (n < 32) o.write(0xa0 | n);
        else if (n < 256) { o.write(0xd9); o.write(n); }
        else if (n < 65536) { o.write(0xda); w16(o, n); }
        else { o.write(0xdb); w32(o, n); }
        o.write(d, 0, n);
    }
}
