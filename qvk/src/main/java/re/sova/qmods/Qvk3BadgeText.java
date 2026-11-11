package re.sova.qmods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BUG-0023: in VK 8.100 the unread counter handed to QVK's drawer badge is a phrase («6 непрочитанных чатов»), not a
 * number. Shown as is, the long pill squeezed the item title out of the row («Сообщения» disappeared). The badge
 * keeps only the number. Pure Java, unit-tested; called from patch 0017 (DrawerBadges.render).
 */
public final class Qvk3BadgeText {
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d\\s\\u00A0]*\\+?)");

    private Qvk3BadgeText() {
    }

    public static String number(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || t.matches("\\d{1,6}\\+?")) return t;
        Matcher m = NUMBER.matcher(t);
        if (!m.find()) return t.length() <= 3 ? t : "";
        return m.group(1).replaceAll("[\\s\\u00A0]", "");
    }
}
