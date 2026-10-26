package com.vkmod.feat;

import com.vkmod.core.Prefs;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** PIN storage (salted SHA-256, never stored in clear). */
public final class AppLock {
    private AppLock() {}

    public static boolean hasPin() { return !Prefs.getString("pin.hash", "").isEmpty(); }

    public static void setPin(String pin) {
        byte[] salt = new byte[8];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        Prefs.putString("pin.salt", s);
        Prefs.putString("pin.hash", hash(s, pin));
    }

    public static void clearPin() { Prefs.remove("pin.salt"); Prefs.remove("pin.hash"); }

    public static boolean check(String pin) {
        String h = Prefs.getString("pin.hash", "");
        return !h.isEmpty() && h.equals(hash(Prefs.getString("pin.salt", ""), pin));
    }

    static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + ":" + pin).getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
