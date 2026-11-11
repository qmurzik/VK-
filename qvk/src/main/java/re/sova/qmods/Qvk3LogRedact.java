package re.sova.qmods;

import java.util.regex.Pattern;

/** Removes secrets from log text before it is shown, copied or saved. Pure Java, covered by unit tests. */
public final class Qvk3LogRedact {
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(access_token|refresh_token|token|secret|password|passwd|sig|signature|api_key|apikey|auth|authorization|exchange_token|anonymous_token|session|cookie)(\"?\\s*[=:]\\s*\"?)([^\\s\"&,;)}\\]]{4,})");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern LONG_HEX = Pattern.compile("\\b[0-9a-fA-F]{40,}\\b");

    private Qvk3LogRedact() {
    }

    public static String clean(String s) {
        if (s == null || s.isEmpty()) return "";
        s = BEARER.matcher(s).replaceAll("$1 <скрыто>");
        s = KEY_VALUE.matcher(s).replaceAll("$1$2<скрыто>");
        s = LONG_HEX.matcher(s).replaceAll("<скрыто>");
        return s;
    }
}
