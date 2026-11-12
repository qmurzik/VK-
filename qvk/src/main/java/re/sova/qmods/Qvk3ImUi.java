package re.sova.qmods;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Shared look of the QVK messenger: colours from the VK theme + the QVK style, avatars, times, statuses. */
final class Qvk3ImUi {
    private Qvk3ImUi() {
    }

    /** Colours of one screen, resolved once per build. */
    static final class Look {
        final Qvk3Ui.Palette p;
        final int inBubble, outBubble, inText, outText, inText2, outText2, accent, link, radius, page;

        Look(Context c) {
            p = Qvk3Sheet.palette(c);
            Qvk3DesignTokens t = Qvk3Appearance.current(c).variant(p.dark);
            page = p.bg;
            accent = p.accent;
            // bubbles from the QVK style, kept readable on the page of the VK theme
            inBubble = p.dark ? Qvk3DesignTokens.mix(p.surface, 0xFFFFFFFF, 0.06f) : (t.incoming != 0 ? t.incoming : 0xFFFFFFFF);
            outBubble = t.outgoing != 0 ? t.outgoing : Qvk3DesignTokens.mix(p.accent, p.dark ? 0xFF000000 : 0xFFFFFFFF, p.dark ? 0.55f : 0.82f);
            inText = Qvk3DesignTokens.ensureContrast(p.text, inBubble, 4.5);
            outText = Qvk3DesignTokens.ensureContrast(p.text, outBubble, 4.5);
            inText2 = Qvk3DesignTokens.ensureContrast(p.text2, inBubble, 3.0);
            outText2 = Qvk3DesignTokens.ensureContrast(p.text2, outBubble, 3.0);
            link = Qvk3DesignTokens.ensureContrast(p.accent, inBubble, 3.0);
            radius = Math.max(8, Math.min(22, t.radius + 6));
        }
    }

    /** Round avatar; a coloured circle with initials while (or when no) photo loads. */
    static void avatar(ImageView v, String url, String name, int sizeDp) {
        Context c = v.getContext();
        int px = Qvk3Ui.dp(c, sizeDp);
        v.setTag(url);
        v.setImageDrawable(new Initials(name, px));
        if (url == null || url.isEmpty() || url.contains("camera_") || url.contains("deactivated")) return;
        Bitmap cached = Qvk3Images.get(c).cached(url, 144);
        if (cached != null) {
            v.setImageDrawable(new Round(cached));
            return;
        }
        Qvk3Images.get(c).load(url, 144, b -> {
            if (b != null && url.equals(v.getTag())) v.setImageDrawable(new Round(b));
        });
    }

    static final int[] AVATAR_COLORS = {0xFFE64646, 0xFFFF9E00, 0xFF4BB34B, 0xFF2688EB, 0xFF8B75CB, 0xFF00B3A4, 0xFFF05C8E};

    /** Circle with the first letters, colour by name (the way VK does it). */
    static final class Initials extends Drawable {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letters;
        private final int size;

        Initials(String name, int size) {
            this.size = size;
            String n = name == null ? "" : name.trim();
            String[] parts = n.split("\\s+");
            String l = parts.length > 1 && !parts[1].isEmpty() ? parts[0].substring(0, 1) + parts[1].substring(0, 1) : n.isEmpty() ? "?" : n.substring(0, 1);
            letters = l.toUpperCase(Locale.ROOT);
            bg.setColor(AVATAR_COLORS[Math.abs(n.hashCode()) % AVATAR_COLORS.length]);
            tx.setColor(0xFFFFFFFF);
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setFakeBoldText(true);
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, bg);
            tx.setTextSize(r * 0.78f);
            c.drawText(letters, b.exactCenterX(), b.exactCenterY() - (tx.ascent() + tx.descent()) / 2f, tx);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Bitmap drawn as a circle (center-crop). */
    static final class Round extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap b;
        private final android.graphics.Matrix m = new android.graphics.Matrix();

        Round(Bitmap b) {
            this.b = b;
            paint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect r = getBounds();
            float s = Math.max(r.width() / (float) b.getWidth(), r.height() / (float) b.getHeight());
            m.setScale(s, s);
            m.postTranslate(r.left + (r.width() - b.getWidth() * s) / 2f, r.top + (r.height() - b.getHeight() * s) / 2f);
            paint.getShader().setLocalMatrix(m);
            c.drawCircle(r.exactCenterX(), r.exactCenterY(), Math.min(r.width(), r.height()) / 2f, paint);
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** «14:05» today, «вчера», «пн», «12 мар», «12.03.24». */
    static String listTime(long unix) {
        if (unix <= 0) return "";
        Calendar c = Calendar.getInstance(), now = Calendar.getInstance();
        c.setTimeInMillis(unix * 1000);
        Locale ru = new Locale("ru");
        if (same(c, now)) return new SimpleDateFormat("HH:mm", ru).format(c.getTime());
        Calendar y = (Calendar) now.clone();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (same(c, y)) return "вчера";
        long days = (now.getTimeInMillis() - c.getTimeInMillis()) / 86_400_000L;
        if (days < 7) return new SimpleDateFormat("EE", ru).format(c.getTime());
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) return new SimpleDateFormat("d MMM", ru).format(c.getTime()).replace(".", "");
        return new SimpleDateFormat("dd.MM.yy", ru).format(c.getTime());
    }

    static String clock(long unix) {
        return new SimpleDateFormat("HH:mm", new Locale("ru")).format(unix * 1000);
    }

    /** Date separator in a chat: «Сегодня», «Вчера», «12 марта», «12 марта 2023». */
    static String day(long unix) {
        Calendar c = Calendar.getInstance(), now = Calendar.getInstance();
        c.setTimeInMillis(unix * 1000);
        if (same(c, now)) return "Сегодня";
        Calendar y = (Calendar) now.clone();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (same(c, y)) return "Вчера";
        String f = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? "d MMMM" : "d MMMM yyyy";
        return new SimpleDateFormat(f, new Locale("ru")).format(c.getTime());
    }

    static boolean same(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a * 1000);
        y.setTimeInMillis(b * 1000);
        return same(x, y);
    }

    /** Header status: «печатает…», «в сети», «был(а) в 14:05», «24 участника». */
    static String status(Qvk3ImStore s, Qvk3ImModel.Conversation c) {
        if (c == null) return "";
        String typing = s.typingText(c.peer, c.chat());
        if (!typing.isEmpty()) return typing;
        if (c.chat()) return c.members > 0 ? Qvk3SettingsActivity.plural(c.members, "участник", "участника", "участников") : "беседа";
        if (c.peer < 0) return "сообщество";
        if (c.online) return c.mobile ? "в сети с телефона" : "в сети";
        if (c.lastSeen > 0) {
            String t = listTime(c.lastSeen);
            return "был(а) в сети " + (t.contains(":") ? "в " + t : t);
        }
        return "";
    }

    static void hideKeyboard(android.view.View v) {
        android.view.inputmethod.InputMethodManager im = v.getContext().getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    static void showKeyboard(android.view.View v) {
        v.requestFocus();
        android.view.inputmethod.InputMethodManager im = v.getContext().getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (im != null) v.post(() -> im.showSoftInput(v, 0));
    }

    /** Opens a link: vk.com links inside this VK (its own router), others in the browser. */
    static void openLink(android.app.Activity a, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            String host = android.net.Uri.parse(url).getHost();
            if (host != null && (host.endsWith("vk.com") || host.endsWith("vk.ru") || host.endsWith("vk.me"))) i.setPackage(a.getPackageName());
            a.startActivity(i);
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось открыть ссылку");
        }
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " КБ";
        return String.format(Locale.ROOT, "%.1f МБ", bytes / 1048576f).replace('.', ',');
    }

    static String duration(int s) {
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
