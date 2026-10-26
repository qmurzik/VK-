package com.vkmod.core;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Diagnostic log of the mod: in-memory ring buffer plus an on-disk file that survives restarts. */
public final class ModLog {
    private static final String TAG = "VKMod";
    private static final int MAX_LINES = 600;
    private static final long MAX_FILE = 256 * 1024;
    private static final ArrayDeque<String> RING = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static volatile File file;

    private ModLog() {}

    public static void init(File dir) {
        try {
            File f = new File(dir, "vkmod.log");
            if (f.exists() && f.length() > MAX_FILE) f.delete();
            file = f;
        } catch (Throwable ignored) {
        }
    }

    public static void i(String msg) { add("I", msg, null); }
    public static void w(String msg) { add("W", msg, null); }
    public static void w(String msg, Throwable t) { add("W", msg, t); }
    public static void e(String msg, Throwable t) { add("E", msg, t); }

    private static synchronized void add(String level, String msg, Throwable t) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(FMT.format(new Date())).append(' ').append(level).append(' ').append(msg);
            if (t != null) sb.append(" | ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            String line = sb.toString();
            if (level.equals("E") || level.equals("W")) Log.w(TAG, line); else Log.i(TAG, line);
            RING.addLast(line);
            while (RING.size() > MAX_LINES) RING.removeFirst();
            File f = file;
            if (f != null) {
                try (FileWriter w = new FileWriter(f, true)) {
                    w.write(line);
                    w.write('\n');
                    if (t != null) {
                        for (StackTraceElement el : t.getStackTrace()) {
                            w.write("    at " + el + "\n");
                            if (el.getClassName().startsWith("com.vkmod") ) break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : RING) sb.append(s).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        RING.clear();
        File f = file;
        if (f != null) f.delete();
    }
}
