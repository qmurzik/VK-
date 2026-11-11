package re.sova.qmods;

/**
 * Better GIF frames for Telegram stickers sent to VK (BUG-0015). QVK 2.x writes every frame with a fixed 3-3-2 palette
 * (8 red × 8 green × 4 blue levels), rounding down, and treats a pixel as transparent below 50 % alpha. That made
 * smooth colours banded and shifted, and half-transparent parts vanished. Before the 2.x encoder maps a frame
 * (patch 0013), this applies ordered (Bayer 8×8) dithering: colours average out to the right shade, and partial
 * transparency becomes a fine stipple that still reads as see-through. GIF itself has no partial alpha, so this is
 * the closest the format allows. Pure Java, covered by unit tests.
 */
public final class Qvk3GifQuant {
    private static final int[] BAYER = {
            0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21};

    private Qvk3GifQuant() {
    }

    /** Rewrites ARGB pixels in place so that StickerGif's fixed palette mapping gives a dithered result. */
    public static void prepare(int[] argb, int width) {
        if (argb == null || width <= 0) return;
        for (int i = 0; i < argb.length; i++) {
            int x = i % width, y = i / width;
            float t = (BAYER[(y & 7) * 8 + (x & 7)] + 0.5f) / 64f;    // 0..1
            int c = argb[i];
            int a = c >>> 24;
            if (a * (1f / 255f) <= t * 0.98f + 0.01f) {
                argb[i] = 0;                                         // transparent at this spot of the pattern
                continue;
            }
            int r = level((c >> 16) & 255, 7, t), g = level((c >> 8) & 255, 7, t), b = level(c & 255, 3, t);
            argb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Picks the lower or upper palette level by the dither threshold, then returns a value that StickerGif.color()
     * maps back to exactly that level ((v * steps) / 255).
     */
    static int level(int v, int steps, float t) {
        float exact = v * steps / 255f;
        int lo = (int) Math.floor(exact);
        int lv = (exact - lo) > t ? Math.min(steps, lo + 1) : lo;
        return lv == steps ? 255 : (int) Math.ceil(lv * 255f / steps);
    }
}
