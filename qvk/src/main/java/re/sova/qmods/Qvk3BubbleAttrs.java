package re.sova.qmods;

import java.util.Map;

/**
 * BUG-0016: QVK 2's palette ({@code ThemePalette.color}) recolours {@code im_bubble_incoming/outgoing}, but in a chat
 * with a wallpaper VK draws bubbles from {@code im_bubble_wallpaper_incoming/outgoing} — so with a chat background
 * the bubbles kept VK's stock dark-theme colours while the palette preview showed the chosen ones (patch 0039).
 */
public final class Qvk3BubbleAttrs {
    private Qvk3BubbleAttrs() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void wallpaper(Map map, String attr, int color) {
        try {
            if (map == null || attr == null) return;
            int at = attr.indexOf("im_bubble_");
            if (at < 0 || attr.endsWith("_alternate")) return;
            String dir = attr.substring(at + "im_bubble_".length());
            if (!"incoming".equals(dir) && !"outgoing".equals(dir)) return;
            String name = attr.substring(0, at) + "im_bubble_wallpaper_" + dir;
            int id = NativeTheme.id(RuntimeBridge.context, "attr", name);
            if (id != 0) map.put(id, color);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.palette.wallpaperBubble", t);
        }
    }
}
