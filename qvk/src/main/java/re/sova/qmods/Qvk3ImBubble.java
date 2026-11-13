package re.sova.qmods;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the content of one message: reply quote, text, every attachment kind, forwarded messages (nested), time,
 * «изм.» and read ticks, reactions. Pure view building; actions go back through {@link Host}.
 */
final class Qvk3ImBubble {
    interface Host {
        void openPhoto(List<Qvk3ImModel.Attachment> photos, int index);

        void openAttachment(Qvk3ImModel.Attachment a);

        void playVoice(Qvk3ImModel.Message m, Qvk3ImModel.Attachment a, Wave view);

        void jumpTo(Qvk3ImModel.Message reply);

        void react(Qvk3ImModel.Message m, int reaction);

        Map<Long, Qvk3ImModel.Person> people();

        String reactionUrl(int id);

        long outRead();
    }

    private final Activity a;
    private final Qvk3ImUi.Look look;
    private final Host host;
    private final int maxW;

    Qvk3ImBubble(Activity a, Qvk3ImUi.Look look, Host host) {
        this.a = a;
        this.look = look;
        this.host = host;
        maxW = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(72), dp(420));
    }

    int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    /** Bubble with grouped corners: the corner on the sender's side is small when the next message is from the same sender. */
    View bubble(Qvk3ImModel.Message m, boolean chat, boolean firstOfGroup, boolean lastOfGroup) {
        boolean out = m.out;
        if (m.onlySticker()) return stickerOnly(m);
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        int bg = out ? look.outBubble : look.inBubble;
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        float r = dp(look.radius), s = dp(5);
        float tl = !out && !firstOfGroup ? s : r, bl = !out && !lastOfGroup ? s : r, tr = out && !firstOfGroup ? s : r, br = out && !lastOfGroup ? s : r;
        d.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        b.setBackground(d);
        b.setClipToOutline(true);
        int text = out ? look.outText : look.inText, text2 = out ? look.outText2 : look.inText2;
        if (chat && !out && firstOfGroup) {
            Qvk3ImModel.Person p = host.people().get(m.from);
            TextView name = Qvk3Ui.line(a, p == null ? "" : p.name, 14, Qvk3ImUi.AVATAR_COLORS[Math.abs((p == null ? "" : p.name).hashCode()) % Qvk3ImUi.AVATAR_COLORS.length], true);
            name.setPadding(dp(12), dp(7), dp(12), 0);
            if (p != null) b.addView(name, new LinearLayout.LayoutParams(-2, -2));
        }
        if (m.reply != null) b.addView(quote(m.reply, out), margins(dp(10), dp(8), dp(10), 0));
        content(b, m, out, text, text2, 0);
        b.addView(footer(m, text2));
        return b;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(l, t, r, bottom);
        return lp;
    }

    /** Text + attachments + forwards of a message (depth > 0: inside a forward). */
    private void content(LinearLayout b, Qvk3ImModel.Message m, boolean out, int text, int text2, int depth) {
        if (!m.text.isEmpty()) {
            TextView t = Qvk3Ui.text(a, m.text, 16, text, false);
            t.setLineSpacing(0, 1.12f);
            t.setLinkTextColor(out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 3.0) : look.link);
            Linkify.addLinks(t, Linkify.WEB_URLS);
            t.setMovementMethod(LinkMovementMethod.getInstance());
            t.setFocusable(false);
            t.setMaxWidth(maxW - dp(24));
            t.setPadding(dp(12), dp(7), dp(12), dp(1));
            b.addView(t, new LinearLayout.LayoutParams(-2, -2)); // wrap: a match_parent child would take the width of its narrowest sibling
        }
        List<Qvk3ImModel.Attachment> photos = new ArrayList<>();
        for (Qvk3ImModel.Attachment x : m.attachments) if (x.type.equals("photo") || x.type.equals("video")) photos.add(x);
        if (!photos.isEmpty()) b.addView(grid(photos), margins(dp(3), dp(3), dp(3), 0));
        for (Qvk3ImModel.Attachment x : m.attachments) {
            if (x.type.equals("photo") || x.type.equals("video")) continue;
            View v = attachment(m, x, out, text, text2);
            if (v != null) b.addView(v, margins(dp(10), dp(6), dp(10), 0));
        }
        if (depth < 3) for (Qvk3ImModel.Message f : m.forwards) b.addView(forwarded(f, out, text, text2, depth + 1), margins(dp(10), dp(6), dp(10), 0));
    }

    private View forwarded(Qvk3ImModel.Message f, boolean out, int text, int text2, int depth) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(new Bar(out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.2) : look.accent, dp(2)));
        box.setPadding(dp(10), 0, 0, 0);
        Qvk3ImModel.Person p = host.people().get(f.from);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Qvk3Ui.line(a, p == null ? "Пересланное сообщение" : p.name, 14, out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 3.0) : look.accent, true);
        head.addView(name, new LinearLayout.LayoutParams(-2, -2));
        if (f.date > 0) head.addView(Qvk3Ui.text(a, "  " + Qvk3ImUi.listTime(f.date), 12, text2, false));
        box.addView(head, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(-dp(12), 0, -dp(10), 0);
        content(inner, f, out, text, text2, depth);
        if (f.text.isEmpty() && f.attachments.isEmpty() && f.forwards.isEmpty()) {
            TextView none = Qvk3Ui.text(a, "Содержимое не пришло от ВК", 14, text2, false);
            none.setPadding(dp(12), dp(2), dp(12), 0);
            inner.addView(none, new LinearLayout.LayoutParams(-2, -2));
        }
        box.addView(inner, new LinearLayout.LayoutParams(-2, -2));
        return box;
    }

    /** Reply quote: accent bar, author, one line of the replied message; tap jumps to it. */
    private View quote(Qvk3ImModel.Message r, boolean out) {
        LinearLayout q = new LinearLayout(a);
        q.setOrientation(LinearLayout.VERTICAL);
        int acc = out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.6) : look.accent;
        q.setBackground(new Bar(acc, dp(2)));
        q.setPadding(dp(10), dp(1), dp(4), dp(1));
        Qvk3ImModel.Person p = host.people().get(r.from);
        q.addView(Qvk3Ui.line(a, p == null ? "Ответ" : p.name, 14, acc, true), new LinearLayout.LayoutParams(-2, -2));
        TextView t = Qvk3Ui.line(a, Qvk3ImModel.preview(r, host.people()), 14, out ? look.outText : look.inText, false);
        t.setMaxWidth(maxW - dp(40));
        q.addView(t, new LinearLayout.LayoutParams(-2, -2));
        q.setOnClickListener(v -> host.jumpTo(r));
        return q;
    }

    private View footer(Qvk3ImModel.Message m, int text2) {
        LinearLayout f = new LinearLayout(a);
        f.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        f.setPadding(dp(12), dp(1), dp(10), dp(6));
        if (m.important) {
            ImageView star = new ImageView(a);
            star.setImageDrawable(Qvk3Icons.of(Qvk3Icons.STAR, text2));
            f.addView(star, new LinearLayout.LayoutParams(dp(12), dp(12)));
        }
        String s = (m.edited() ? "изм. " : "") + (m.date > 0 ? Qvk3ImUi.clock(m.date) : "");
        if (m.local == 1) s = "отправка…";
        if (m.local == 2) s = "не отправлено · нажмите";
        TextView t = Qvk3Ui.text(a, s, 11, m.local == 2 ? 0xFFE64646 : text2, false);
        f.addView(t);
        if (m.out && m.local == 0) {
            ImageView tick = new ImageView(a);
            boolean read = (m.cmid > 0 ? m.cmid : m.id) <= host.outRead();
            tick.setImageDrawable(new Ticks(read ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.4) : text2, read));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(12));
            lp.leftMargin = dp(3);
            f.addView(tick, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        f.setLayoutParams(lp);
        return f;
    }

    /** Reactions as chips under the bubble; tap = toggle your own. */
    View reactions(Qvk3ImModel.Message m) {
        if (m.reactions.isEmpty()) return null;
        LinearLayout row = new LinearLayout(a);
        for (Qvk3ImModel.Reaction r : m.reactions) {
            LinearLayout chip = new LinearLayout(a);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(6), dp(3), dp(9), dp(3));
            chip.setBackground(Qvk3Ui.rounded(r.mine ? (look.accent & 0x00FFFFFF) | 0x33000000 : (look.p.dark ? 0x26FFFFFF : 0x14000000), dp(14)));
            ImageView img = new ImageView(a);
            String url = host.reactionUrl(r.id);
            if (!url.isEmpty()) Qvk3Images.get(a).load(url, 96, bm -> {
                if (bm != null) img.setImageBitmap(bm);
            });
            chip.addView(img, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView n = Qvk3Ui.text(a, String.valueOf(r.count), 13, r.mine ? look.accent : look.p.text, true);
            n.setPadding(dp(4), 0, 0, 0);
            chip.addView(n);
            chip.setOnClickListener(v -> host.react(m, r.mine ? 0 : r.id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(4);
            row.addView(chip, lp);
        }
        return row;
    }

    private View stickerOnly(Qvk3ImModel.Message m) {
        Qvk3ImModel.Attachment s = m.attachments.get(0);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(m.out ? Gravity.END : Gravity.START);
        ImageView img = image(s.preview, 512);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = s.type.equals("graffiti") ? dp(200) : dp(150);
        box.addView(img, new LinearLayout.LayoutParams(size, s.type.equals("graffiti") && s.width > 0 ? size * s.height / Math.max(1, s.width) : size));
        TextView t = Qvk3Ui.text(a, Qvk3ImUi.clock(m.date), 11, look.p.text2, false);
        t.setPadding(dp(6), 0, dp(6), 0);
        box.addView(t);
        return box;
    }

    // ------------------------------------------------------------------ attachments

    private ImageView image(String url, int size) {
        ImageView v = new ImageView(a);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackgroundColor(look.p.dark ? 0x22FFFFFF : 0x11000000);
        v.setTag(url);
        if (url != null && !url.isEmpty()) {
            android.graphics.Bitmap c = Qvk3Images.get(a).cached(url, size);
            if (c != null) v.setImageBitmap(c);
            else Qvk3Images.get(a).load(url, size, b -> {
                if (b != null && url.equals(v.getTag())) v.setImageBitmap(b);
            });
        }
        return v;
    }

    /** Photos and videos: one large, or a grid of two columns (like VK). */
    private View grid(List<Qvk3ImModel.Attachment> items) {
        int w = maxW - dp(6);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        List<Qvk3ImModel.Attachment> photos = new ArrayList<>();
        for (Qvk3ImModel.Attachment x : items) if (x.type.equals("photo")) photos.add(x);
        if (items.size() == 1) {
            Qvk3ImModel.Attachment x = items.get(0);
            int h = x.width > 0 ? Math.min(dp(420), Math.max(dp(120), w * x.height / x.width)) : w * 3 / 4;
            col.addView(tile(x, photos, w, h));
        } else {
            for (int i = 0; i < items.size(); i += 2) {
                LinearLayout row = new LinearLayout(a);
                int cell = i + 1 < items.size() ? (w - dp(2)) / 2 : w;
                int h = i + 1 < items.size() ? cell : w * 2 / 3;
                row.addView(tile(items.get(i), photos, cell, h));
                if (i + 1 < items.size()) {
                    View gap = new View(a);
                    row.addView(gap, new LinearLayout.LayoutParams(dp(2), 1));
                    row.addView(tile(items.get(i + 1), photos, cell, h));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) lp.topMargin = dp(2);
                col.addView(row, lp);
            }
        }
        Qvk3Ui.clipRound(col, dp(Math.max(4, look.radius - 4)));
        return col;
    }

    private View tile(Qvk3ImModel.Attachment x, List<Qvk3ImModel.Attachment> photos, int w, int h) {
        FrameLayout f = new FrameLayout(a);
        f.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        f.addView(image(x.preview, Math.min(900, w)), new FrameLayout.LayoutParams(-1, -1));
        if (x.type.equals("video")) {
            ImageView play = new ImageView(a);
            play.setImageDrawable(Qvk3Icons.of(Qvk3Icons.PLAY, 0xFFFFFFFF));
            play.setBackground(Qvk3Ui.rounded(0x88000000, dp(24)));
            play.setPadding(dp(12), dp(12), dp(12), dp(12));
            f.addView(play, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
            if (x.duration > 0) {
                TextView d = Qvk3Ui.text(a, Qvk3ImUi.duration(x.duration), 12, 0xFFFFFFFF, true);
                d.setPadding(dp(6), dp(2), dp(6), dp(2));
                d.setBackground(Qvk3Ui.rounded(0x99000000, dp(8)));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                lp.setMargins(dp(6), 0, 0, dp(6));
                f.addView(d, lp);
            }
            f.setOnClickListener(v -> host.openAttachment(x));
        } else {
            f.setOnClickListener(v -> host.openPhoto(photos, Math.max(0, photos.indexOf(x))));
        }
        f.setContentDescription(Qvk3ImModel.label(x.type));
        return f;
    }

    /** «Кружок»: the first frame in a circle with a play mark and the length; tap plays it round, in place. */
    private View circle(Qvk3ImModel.Attachment x) {
        int size = dp(200);
        FrameLayout f = new FrameLayout(a);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        f.setClipToOutline(true);
        ImageView img = image(x.preview, 400);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(img, new FrameLayout.LayoutParams(-1, -1));
        ImageView play = new ImageView(a);
        play.setImageDrawable(Qvk3Icons.of(Qvk3Icons.PLAY, 0xFFFFFFFF));
        int pd = dp(14);
        play.setPadding(pd, pd, pd, pd);
        android.graphics.drawable.GradientDrawable pb = new android.graphics.drawable.GradientDrawable();
        pb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        pb.setColor(0x66000000);
        play.setBackground(pb);
        f.addView(play, new FrameLayout.LayoutParams(dp(52), dp(52), android.view.Gravity.CENTER));
        TextView len = Qvk3Ui.line(a, Qvk3ImUi.duration(x.duration), 12, 0xFFFFFFFF, true);
        len.setPadding(dp(8), dp(2), dp(8), dp(2));
        android.graphics.drawable.GradientDrawable lb = new android.graphics.drawable.GradientDrawable();
        lb.setColor(0x80000000);
        lb.setCornerRadius(dp(10));
        len.setBackground(lb);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        ll.bottomMargin = dp(14);
        f.addView(len, ll);
        f.setContentDescription("Видеосообщение, " + Qvk3ImUi.duration(x.duration));
        f.setOnClickListener(v -> host.openAttachment(x));
        f.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return f;
    }

    private View attachment(Qvk3ImModel.Message m, Qvk3ImModel.Attachment x, boolean out, int text, int text2) {
        switch (x.type) {
            case "sticker":
            case "graffiti": {
                ImageView img = image(x.preview, 512);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setBackground(null);
                img.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(140)));
                return img;
            }
            case "gift": {
                ImageView img = image(x.preview, 256);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setBackground(null);
                img.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(140)));
                return img;
            }
            case "audio_message":
                return voice(m, x, out, text, text2);
            case "video_message":
                return circle(x);
            case "audio":
                return row(Qvk3Icons.MUSIC, x.title, x.subtitle + (x.duration > 0 ? " · " + Qvk3ImUi.duration(x.duration) : ""), out, text, text2, () -> host.openAttachment(x));
            case "doc":
                if (!x.preview.isEmpty() && ("gif".equalsIgnoreCase(x.ext) || x.width > 0)) {
                    ImageView img = image(x.preview, 600);
                    int w = maxW - dp(20), h = x.width > 0 ? Math.min(dp(320), w * x.height / Math.max(1, x.width)) : w * 2 / 3;
                    img.setLayoutParams(new LinearLayout.LayoutParams(w, h));
                    Qvk3Ui.clipRound(img, dp(10));
                    img.setOnClickListener(v -> host.openAttachment(x));
                    return img;
                }
                return row(Qvk3Icons.FOLDER, x.title, (x.ext.isEmpty() ? "" : x.ext.toUpperCase() + " · ") + Qvk3ImUi.size(x.size), out, text, text2, () -> host.openAttachment(x));
            case "link":
                return card(x.preview, x.title.isEmpty() ? x.url : x.title, x.subtitle.isEmpty() ? android.net.Uri.parse(x.url).getHost() : x.subtitle, out, text, text2, () -> host.openAttachment(x));
            case "wall":
            case "wall_reply":
                return card("", x.title, x.subtitle, out, text, text2, () -> host.openAttachment(x));
            case "poll":
            case "market":
            case "story":
                return card(x.preview, x.title, x.subtitle, out, text, text2, () -> host.openAttachment(x));
            case "call":
                return row(Qvk3Icons.BELL, x.title, x.subtitle + (x.duration > 0 ? " · " + Qvk3ImUi.duration(x.duration) : ""), out, text, text2, null);
            case "geo":
                return row(Qvk3Icons.HOME, x.title, "Открыть на карте", out, text, text2, () -> host.openAttachment(x));
            default:
                return row(Qvk3Icons.INFO, Qvk3ImModel.label(x.type), "Открыть в ВК", out, text, text2, () -> host.openAttachment(x));
        }
    }

    private View row(String icon, String title, String sub, boolean out, int text, int text2, Runnable click) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Qvk3Icons.of(icon, 0xFFFFFFFF));
        ic.setPadding(dp(10), dp(10), dp(10), dp(10));
        ic.setBackground(Qvk3Ui.rounded(out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.0) : look.accent, dp(20)));
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(10), 0, 0, 0);
        TextView tt = Qvk3Ui.line(a, title, 15, text, true);
        tt.setMaxWidth(maxW - dp(80));
        t.addView(tt);
        if (sub != null && !sub.isEmpty()) t.addView(Qvk3Ui.line(a, sub, 13, text2, false));
        r.addView(t);
        if (click != null) r.setOnClickListener(v -> click.run());
        return r;
    }

    private View card(String img, String title, String sub, boolean out, int text, int text2, Runnable click) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(new Bar(out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.2) : look.accent, dp(2)));
        c.setPadding(dp(10), 0, 0, 0);
        if (img != null && !img.isEmpty()) {
            ImageView iv = image(img, 600);
            iv.setLayoutParams(new LinearLayout.LayoutParams(maxW - dp(40), (maxW - dp(40)) / 2));
            Qvk3Ui.clipRound(iv, dp(8));
            c.addView(iv);
        }
        TextView tt = Qvk3Ui.text(a, title == null ? "" : title, 15, text, true);
        tt.setMaxLines(2);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        tt.setMaxWidth(maxW - dp(40));
        tt.setPadding(0, dp(4), 0, 0);
        c.addView(tt);
        if (sub != null && !sub.isEmpty()) {
            TextView st = Qvk3Ui.text(a, sub, 13, text2, false);
            st.setMaxLines(3);
            st.setEllipsize(TextUtils.TruncateAt.END);
            st.setMaxWidth(maxW - dp(40));
            c.addView(st);
        }
        if (click != null) c.setOnClickListener(v -> click.run());
        return c;
    }

    private View voice(Qvk3ImModel.Message m, Qvk3ImModel.Attachment x, boolean out, int text, int text2) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int acc = out ? Qvk3DesignTokens.ensureContrast(look.accent, look.outBubble, 2.0) : look.accent;
        ImageView play = new ImageView(a);
        play.setImageDrawable(Qvk3Icons.of(Qvk3Icons.PLAY, 0xFFFFFFFF));
        play.setPadding(dp(11), dp(11), dp(9), dp(11));
        play.setBackground(Qvk3Ui.rounded(acc, dp(20)));
        r.addView(play, new LinearLayout.LayoutParams(dp(40), dp(40)));
        Wave wave = new Wave(a, x.waveform, acc, (text2 & 0x00FFFFFF) | 0x66000000, play);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Math.min(dp(180), maxW - dp(110)), dp(28));
        wl.leftMargin = dp(10);
        r.addView(wave, wl);
        TextView d = Qvk3Ui.text(a, Qvk3ImUi.duration(x.duration), 12, text2, false);
        d.setPadding(dp(8), 0, 0, 0);
        r.addView(d);
        wave.durationLabel = d;
        wave.duration = x.duration;
        box.addView(r);
        View.OnClickListener toggle = v -> host.playVoice(m, x, wave);
        play.setOnClickListener(toggle);
        wave.setOnClickListener(toggle);
        if (!x.transcript.isEmpty()) {
            TextView tr = Qvk3Ui.text(a, x.transcript, 14, text, false);
            tr.setPadding(0, dp(6), 0, 0);
            tr.setMaxWidth(maxW - dp(24));
            box.addView(tr);
        }
        return box;
    }

    // ------------------------------------------------------------------ small drawables / views

    /** Thin accent bar on the left (quotes, forwards, cards). */
    static final class Bar extends android.graphics.drawable.Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int w;

        Bar(int color, int w) {
            p.setColor(color);
            this.w = w;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawRoundRect(b.left, b.top, b.left + w, b.bottom, w, w, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** One tick «sent», two ticks «read». */
    static final class Ticks extends android.graphics.drawable.Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean two;

        Ticks(int color, boolean two) {
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            this.two = two;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float h = b.height(), u = h / 12f;
            p.setStrokeWidth(1.6f * u);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(b.left + 1 * u, b.top + 6.5f * u);
            path.lineTo(b.left + 4.5f * u, b.top + 10 * u);
            path.lineTo(b.left + 11 * u, b.top + 2.5f * u);
            if (two) {
                path.moveTo(b.left + 8.5f * u, b.top + 9.5f * u);
                path.lineTo(b.left + 9 * u, b.top + 10 * u);
                path.lineTo(b.left + 16 * u, b.top + 2.5f * u);
            }
            c.drawPath(path, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Voice message waveform; the played part is drawn in the accent colour. */
    static final class Wave extends View {
        private final int[] data;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ImageView playButton;
        TextView durationLabel;
        int duration;
        float progress;

        Wave(android.content.Context c, int[] data, int active, int idle, ImageView playButton) {
            super(c);
            this.data = data == null || data.length == 0 ? new int[]{4, 8, 12, 6, 10, 14, 8, 5, 9, 12, 7, 4} : data;
            on.setColor(active);
            off.setColor(idle);
            this.playButton = playButton;
        }

        void setProgress(float f, boolean playing) {
            progress = f;
            Qvk3Ui.setIcon(playButton, playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, 0xFFFFFFFF);
            if (durationLabel != null) durationLabel.setText(Qvk3ImUi.duration(playing || f > 0 ? Math.round(duration * f) : duration));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float bw = Math.max(2f, getResources().getDisplayMetrics().density * 2.2f), gap = bw * 0.8f;
            int bars = Math.max(1, (int) (w / (bw + gap)));
            for (int i = 0; i < bars; i++) {
                int v = data[Math.min(data.length - 1, i * data.length / bars)];
                float bh = Math.max(bw, h * (0.12f + 0.88f * Math.min(31, v) / 31f));
                float x = i * (bw + gap), y = (h - bh) / 2f;
                c.drawRoundRect(x, y, x + bw, y + bh, bw / 2f, bw / 2f, i < bars * progress ? on : off);
            }
        }
    }
}
