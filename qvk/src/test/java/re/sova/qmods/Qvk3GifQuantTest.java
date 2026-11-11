package re.sova.qmods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Qvk3GifQuantTest {
    private static int mapR(int c) { return (((c >> 16) & 255) * 7) / 255; }  // what StickerGif.color() does

    @Test
    public void levelsRoundTripThroughTheFixedPalette() {
        for (int lv = 0; lv <= 7; lv++) {
            int v = Qvk3GifQuant.level(Math.round(lv * 255f / 7), 7, 0.5f);
            assertEquals(lv, (v * 7) / 255);
        }
    }

    @Test
    public void averageColourIsPreserved() {
        int w = 64, h = 64;
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFF000000 | (180 << 16));
        Qvk3GifQuant.prepare(px, w);
        double sum = 0;
        for (int c : px) sum += mapR(c) * 255.0 / 7;
        assertEquals(180, sum / px.length, 6);
    }

    @Test
    public void halfTransparentBecomesAStipple() {
        int w = 64, h = 64;
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0x80FFFFFF);
        Qvk3GifQuant.prepare(px, w);
        int visible = 0;
        for (int c : px) if ((c >>> 24) >= 128) visible++;
        assertTrue(visible > px.length * 0.4 && visible < px.length * 0.6);
        int[] clear = new int[16];
        Qvk3GifQuant.prepare(clear, 4);
        for (int c : clear) assertEquals(0, c);
    }
}
