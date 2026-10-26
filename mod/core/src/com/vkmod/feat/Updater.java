package com.vkmod.feat;

import com.vkmod.Mod;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Looks up the latest mod release on GitHub. */
public final class Updater {
    private Updater() {}

    public static final String REPO = "qmurzik/VK-";

    public static final class Result {
        public final boolean newer; public final String tag, url, error;
        Result(boolean n, String t, String u, String e) { newer = n; tag = t; url = u; error = e; }
    }

    /** Blocking; call from a worker thread. */
    public static Result check() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "vkmod");
            if (c.getResponseCode() == 404) return new Result(false, "", "", "Релизов пока нет");
            if (c.getResponseCode() != 200) return new Result(false, "", "", "GitHub вернул " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
            JSONObject o = new JSONObject(sb.toString());
            String tag = o.optString("tag_name", "");
            return new Result(newer(tag, Mod.VERSION), tag, o.optString("html_url", ""), null);
        } catch (Throwable t) {
            return new Result(false, "", "", t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    static boolean newer(String remote, String local) {
        int[] a = parts(remote), b = parts(local);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parts(String v) {
        String[] p = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) { try { r[i] = Integer.parseInt(p[i]); } catch (NumberFormatException e) { r[i] = 0; } }
        return r;
    }
}
