package re.sova.qmods;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The QVK window: a bottom sheet in the colours of the current VK theme (the palette QVK 2 menus use, MenuUi), with a handle,
 * a left-aligned title, readable text (bullet lists rendered as lists), full-width rows for choices, one filled main button
 * and tonal secondary ones; it slides up and down.
 *
 * It is a drop-in {@link AlertDialog.Builder}: {@link Qvk3Ui#dialog} returns it, so every QVK 3 dialog gets the same look
 * without touching its code; {@link AlertDialog#getButton} returns the sheet's own buttons.
 */
final class Qvk3Sheet {
    private Qvk3Sheet() {
    }

    /** Colours of the current VK theme (as the QVK 2 menus and the settings centre use), with a fallback. */
    static Qvk3Ui.Palette palette(Context c) {
        try {
            MenuUi mu = new MenuUi(c);
            return new Qvk3Ui.Palette(mu.dark, mu.background, mu.surface, mu.soft, mu.text, mu.secondary, mu.accent, Color.WHITE, mu.line);
        } catch (Throwable t) {
            return Qvk3Ui.palette(c, "modern");
        }
    }

    /** Small uppercase group caption, like the section labels of the settings centre. */
    static android.widget.TextView section(Context c, Qvk3Ui.Palette p, String title) {
        android.widget.TextView t = Qvk3Ui.text(c, title.toUpperCase(java.util.Locale.ROOT), 12, p.text2, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(Qvk3Ui.dp(c, 4), Qvk3Ui.dp(c, 18), 0, Qvk3Ui.dp(c, 8));
        return t;
    }

    /** Rounded group of rows on the sheet surface. */
    static android.widget.LinearLayout group(Context c, Qvk3Ui.Palette p) {
        android.widget.LinearLayout l = new android.widget.LinearLayout(c);
        l.setOrientation(android.widget.LinearLayout.VERTICAL);
        l.setBackground(Qvk3Ui.rounded(p.dark ? 0x12FFFFFF : 0x08000000, Qvk3Ui.dp(c, 16)));
        l.setPadding(Qvk3Ui.dp(c, 16), Qvk3Ui.dp(c, 4), Qvk3Ui.dp(c, 16), Qvk3Ui.dp(c, 4));
        return l;
    }

    interface Flag {
        void set(boolean on);
    }

    /** Title (+ optional hint) with a QVK toggle; the whole row toggles. */
    static View switchRow(Context c, Qvk3Ui.Palette p, String title, String hint, boolean on, Flag sink) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Qvk3Ui.dp(c, 56));
        row.setPadding(0, Qvk3Ui.dp(c, 8), 0, Qvk3Ui.dp(c, 8));
        android.widget.LinearLayout txt = new android.widget.LinearLayout(c);
        txt.setOrientation(android.widget.LinearLayout.VERTICAL);
        txt.addView(Qvk3Ui.text(c, title, 16, p.text, false));
        if (hint != null && !hint.isEmpty()) {
            android.widget.TextView h = Qvk3Ui.text(c, hint, 13, p.text2, false);
            h.setPadding(0, Qvk3Ui.dp(c, 2), 0, 0);
            txt.addView(h);
        }
        row.addView(txt, Qvk3Ui.weight(1));
        Qvk3Toggle t = new Qvk3Toggle(c, on, p.accent, p.dark ? 0x33FFFFFF : 0x22000000);
        android.widget.LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
        lp.leftMargin = Qvk3Ui.dp(c, 12);
        row.addView(t, lp);
        row.setOnClickListener(v -> {
            boolean n = !t.isChecked();
            t.setChecked(n, true);
            sink.set(n);
        });
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(t.isChecked());
                info.setClassName(android.widget.Switch.class.getName());
            }
        });
        return row;
    }

    interface Seek {
        /** Formats the value shown at the right; called on every move. */
        String label(int v);
    }

    /** Title on the left, current value on the right in the accent colour, slim slider below. */
    static android.widget.LinearLayout slider(Context c, Qvk3Ui.Palette p, String title, android.widget.SeekBar s, Seek fmt) {
        android.widget.LinearLayout l = new android.widget.LinearLayout(c);
        l.setOrientation(android.widget.LinearLayout.VERTICAL);
        l.setPadding(0, Qvk3Ui.dp(c, 10), 0, Qvk3Ui.dp(c, 2));
        android.widget.LinearLayout head = new android.widget.LinearLayout(c);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.addView(Qvk3Ui.text(c, title, 15, p.text, false), Qvk3Ui.weight(1));
        android.widget.TextView val = Qvk3Ui.text(c, fmt.label(s.getProgress()), 14, p.accent, true);
        head.addView(val, Qvk3Ui.wrap());
        l.addView(head);
        Qvk3Ui.styleSeek(c, s, p.accent, p.dark ? 0x26FFFFFF : 0x1A000000);
        s.setPadding(Qvk3Ui.dp(c, 8), 0, Qvk3Ui.dp(c, 8), 0);
        s.setContentDescription(title);
        l.addView(s, Qvk3Ui.match());
        l.setTag(val);
        return l;
    }

    /** Value label of a {@link #slider} row. */
    static android.widget.TextView sliderValue(View row) {
        return (android.widget.TextView) row.getTag();
    }

    /** Filled rounded text field matching the report form. */
    static <T extends android.widget.EditText> T field(Context c, Qvk3Ui.Palette p, T e) {
        e.setTextColor(p.text);
        e.setHintTextColor(p.text2);
        e.setTextSize(15);
        e.setBackground(Qvk3Ui.rounded(p.dark ? 0x14FFFFFF : 0x0A000000, Qvk3Ui.dp(c, 14)));
        e.setPadding(Qvk3Ui.dp(c, 14), Qvk3Ui.dp(c, 12), Qvk3Ui.dp(c, 14), Qvk3Ui.dp(c, 12));
        return e;
    }

    /** Tappable row: title, optional value on the right, chevron. "Title: value" labels are split automatically. */
    static View navRow(Context c, Qvk3Ui.Palette p, String label, View.OnClickListener l) {
        String title = label, value = null;
        int k = label.indexOf(": ");
        if (k > 0 && k < 40) {
            title = label.substring(0, k);
            value = label.substring(k + 2);
        }
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Qvk3Ui.dp(c, 52));
        row.setPadding(0, Qvk3Ui.dp(c, 8), 0, Qvk3Ui.dp(c, 8));
        android.widget.TextView t = Qvk3Ui.text(c, title, 16, p.text, false);
        row.addView(t, Qvk3Ui.weight(1));
        if (value != null) {
            android.widget.TextView v = Qvk3Ui.text(c, value, 14, p.text2, false);
            row.setTag(v);
            v.setMaxLines(1);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setMaxWidth(Qvk3Ui.dp(c, 160));
            v.setPadding(Qvk3Ui.dp(c, 12), 0, Qvk3Ui.dp(c, 4), 0);
            row.addView(v, Qvk3Ui.wrap());
        }
        android.widget.ImageView chev = new android.widget.ImageView(c);
        chev.setImageDrawable(Qvk3Icons.of(Qvk3Icons.RIGHT, (p.text2 & 0x00FFFFFF) | 0x99000000));
        row.addView(chev, new android.widget.LinearLayout.LayoutParams(Qvk3Ui.dp(c, 16), Qvk3Ui.dp(c, 16)));
        row.setContentDescription(label);
        row.setOnClickListener(l);
        Qvk3Motion.press(row);
        return row;
    }

    /** Updates the right-hand value of a {@link #navRow} built from a "Title: value" label. */
    static void setNavValue(View row, String value) {
        if (row.getTag() instanceof android.widget.TextView) ((android.widget.TextView) row.getTag()).setText(value);
    }

    /** Row with a colour swatch on the right; {@link #setSwatch} repaints it. */
    static View swatchRow(Context c, Qvk3Ui.Palette p, String title, int color, View.OnClickListener l) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Qvk3Ui.dp(c, 52));
        row.setPadding(0, Qvk3Ui.dp(c, 6), 0, Qvk3Ui.dp(c, 6));
        row.addView(Qvk3Ui.text(c, title, 16, p.text, false), Qvk3Ui.weight(1));
        android.widget.TextView hex = Qvk3Ui.text(c, "", 13, p.text2, false);
        hex.setTypeface(android.graphics.Typeface.MONOSPACE);
        hex.setPadding(0, 0, Qvk3Ui.dp(c, 10), 0);
        row.addView(hex, Qvk3Ui.wrap());
        View sw = new View(c);
        row.addView(sw, new android.widget.LinearLayout.LayoutParams(Qvk3Ui.dp(c, 28), Qvk3Ui.dp(c, 28)));
        row.setTag(new View[]{hex, sw});
        setSwatch(c, p, row, color);
        row.setContentDescription(title);
        row.setOnClickListener(l);
        Qvk3Motion.press(row);
        return row;
    }

    static void setSwatch(Context c, Qvk3Ui.Palette p, View row, int color) {
        View[] v = (View[]) row.getTag();
        ((android.widget.TextView) v[0]).setText(String.format("#%06X", color & 0xFFFFFF));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(Qvk3Ui.dp(c, 1.5f), p.dark ? 0x40FFFFFF : 0x26000000);
        v[1].setBackground(g);
    }

    /** Thin divider between rows of a {@link #group}. */
    static View divider(Context c, Qvk3Ui.Palette p) {
        View v = new View(c);
        v.setBackgroundColor(p.divider);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, Math.max(1, Qvk3Ui.dp(c, 0.5f))));
        return v;
    }

    /** Small caption under a group. */
    static android.widget.TextView note(Context c, Qvk3Ui.Palette p, String s) {
        android.widget.TextView t = Qvk3Ui.text(c, s, 12, p.text2, false);
        t.setPadding(Qvk3Ui.dp(c, 4), Qvk3Ui.dp(c, 8), Qvk3Ui.dp(c, 4), 0);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** The sheet shown last (a custom view inside it can close it, e.g. the reaction row of the messenger). */
    static volatile android.app.Dialog lastShown;

    static final class Builder extends AlertDialog.Builder {
        private final Context c;
        private CharSequence title, message;
        private View view;
        private CharSequence[] items;
        private DialogInterface.OnClickListener itemsL;
        private int checked = -1;
        private boolean single;
        private boolean[] multi;
        private DialogInterface.OnMultiChoiceClickListener multiL;
        private final CharSequence[] labels = new CharSequence[3];
        private final DialogInterface.OnClickListener[] listeners = new DialogInterface.OnClickListener[3];
        private boolean cancelable = true;
        private DialogInterface.OnCancelListener onCancel;
        private DialogInterface.OnDismissListener onDismiss;

        Builder(Context c) {
            super(c, Qvk3Ui.night(c) ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            this.c = c;
        }

        private CharSequence str(int id) {
            return c.getString(id);
        }

        @Override public AlertDialog.Builder setTitle(CharSequence t) { title = t; return this; }
        @Override public AlertDialog.Builder setTitle(int id) { title = str(id); return this; }
        @Override public AlertDialog.Builder setMessage(CharSequence m) { message = m; return this; }
        @Override public AlertDialog.Builder setMessage(int id) { message = str(id); return this; }
        @Override public AlertDialog.Builder setView(View v) { view = v; return this; }
        @Override public AlertDialog.Builder setIcon(int id) { return this; }
        @Override public AlertDialog.Builder setItems(CharSequence[] it, DialogInterface.OnClickListener l) { items = it; itemsL = l; single = false; return this; }
        @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] it, int sel, DialogInterface.OnClickListener l) { items = it; itemsL = l; checked = sel; single = true; return this; }
        @Override public AlertDialog.Builder setMultiChoiceItems(CharSequence[] it, boolean[] ch, DialogInterface.OnMultiChoiceClickListener l) { items = it; multi = ch; multiL = l; return this; }
        @Override public AlertDialog.Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { labels[0] = t; listeners[0] = l; return this; }
        @Override public AlertDialog.Builder setPositiveButton(int id, DialogInterface.OnClickListener l) { return setPositiveButton(str(id), l); }
        @Override public AlertDialog.Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { labels[1] = t; listeners[1] = l; return this; }
        @Override public AlertDialog.Builder setNeutralButton(int id, DialogInterface.OnClickListener l) { return setNeutralButton(str(id), l); }
        @Override public AlertDialog.Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { labels[2] = t; listeners[2] = l; return this; }
        @Override public AlertDialog.Builder setNegativeButton(int id, DialogInterface.OnClickListener l) { return setNegativeButton(str(id), l); }
        @Override public AlertDialog.Builder setCancelable(boolean b) { cancelable = b; return this; }
        @Override public AlertDialog.Builder setOnCancelListener(DialogInterface.OnCancelListener l) { onCancel = l; return this; }
        @Override public AlertDialog.Builder setOnDismissListener(DialogInterface.OnDismissListener l) { onDismiss = l; return this; }

        @Override
        public AlertDialog create() {
            Sheet d = new Sheet(c);
            d.setCancelable(cancelable);
            d.setCanceledOnTouchOutside(cancelable);
            if (onCancel != null) d.setOnCancelListener(onCancel);
            if (onDismiss != null) d.setOnDismissListener(onDismiss);
            d.build(this);
            return d;
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            lastShown = d;
            return d;
        }
    }

    static final class Sheet extends AlertDialog {
        private final Context c;
        private final Qvk3Ui.Palette p;
        private final Button[] buttons = new Button[3];
        private LinearLayout panel;
        private boolean closing;

        Sheet(Context c) {
            super(c, Qvk3Ui.night(c) ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            this.c = c;
            this.p = palette(c);
        }

        int dp(float v) {
            return Qvk3Ui.dp(c, v);
        }

        void build(Qvk3Sheet.Builder b) {
            FrameLayout outer = new FrameLayout(c);
            panel = new LinearLayout(c) {
                @Override
                protected void onMeasure(int w, int h) {
                    int max = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.9f);
                    int size = MeasureSpec.getSize(h);
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(size > 0 ? Math.min(size, max) : max, MeasureSpec.AT_MOST));
                }
            };
            panel.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(p.surface == 0 ? p.bg : p.surface);
            float r = dp(28);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            panel.setBackground(bg);
            panel.setElevation(dp(16));
            panel.setPadding(0, dp(10), 0, dp(14));
            panel.setClickable(true); // taps on the sheet never fall through to «outside»

            View handle = new View(c);
            handle.setBackground(Qvk3Ui.rounded((p.text2 & 0x00FFFFFF) | 0x66000000, dp(2)));
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
            hl.gravity = Gravity.CENTER_HORIZONTAL;
            hl.bottomMargin = dp(10);
            panel.addView(handle, hl);

            if (!TextUtils.isEmpty(b.title)) {
                TextView t = Qvk3Ui.text(c, b.title, 20, p.text, true);
                t.setPadding(dp(24), dp(6), dp(24), dp(10));
                t.setAccessibilityHeading(true);
                panel.addView(t, Qvk3Ui.match());
            }

            LinearLayout body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            if (!TextUtils.isEmpty(b.message)) {
                TextView m = Qvk3Ui.text(c, bullets(b.message), 15, p.text, false);
                m.setLineSpacing(0, 1.18f);
                m.setTextIsSelectable(false);
                m.setPadding(dp(24), 0, dp(24), dp(12));
                body.addView(m, Qvk3Ui.match());
            }
            if (b.items != null) {
                for (int i = 0; i < b.items.length; i++) body.addView(row(b, i), Qvk3Ui.match());
            }
            if (b.view != null) {
                polish(c, p, b.view);
                if (b.view instanceof android.widget.EditText) {
                    // a bare input passed straight to setView: give it the sheet's side padding
                    FrameLayout f = new FrameLayout(c);
                    f.setPadding(dp(24), 0, dp(24), dp(4));
                    detach(b.view);
                    f.addView(b.view, new FrameLayout.LayoutParams(-1, -2));
                    b.view = f;
                }
            }
            boolean scrollsItself = b.view != null && (b.view instanceof ScrollView || b.view instanceof AbsListView
                    || b.view instanceof android.widget.HorizontalScrollView || containsScroller(b.view));
            if (b.view != null && scrollsItself) {
                if (body.getChildCount() > 0) panel.addView(wrap(body), new LinearLayout.LayoutParams(-1, -2));
                detach(b.view);
                panel.addView(b.view, new LinearLayout.LayoutParams(-1, 0, 1f));
            } else {
                if (b.view != null) {
                    detach(b.view);
                    body.addView(b.view, Qvk3Ui.match());
                }
                if (body.getChildCount() > 0) panel.addView(wrap(body), new LinearLayout.LayoutParams(-1, 0, 1f));
            }

            LinearLayout actions = new LinearLayout(c);
            actions.setOrientation(LinearLayout.VERTICAL);
            actions.setPadding(dp(20), dp(8), dp(20), 0);
            // order: main action (filled), the alternative (tonal), then the quiet «cancel/close»
            int[] order = {0, 1, 2};
            boolean any = false;
            for (int which : order) {
                if (b.labels[which] == null) continue;
                boolean primary = which == 0; // a neutral action is never the loud one
                boolean quiet = which == 2 && (b.labels[0] != null || b.labels[1] != null);
                Button btn = button(b.labels[which], primary, quiet);
                final int code = which == 0 ? BUTTON_POSITIVE : which == 1 ? BUTTON_NEUTRAL : BUTTON_NEGATIVE;
                final DialogInterface.OnClickListener l = b.listeners[which];
                btn.setOnClickListener(v -> {
                    if (l != null) l.onClick(this, code);
                    dismiss();
                });
                buttons[which] = btn;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(quiet ? 46 : 52));
                if (any) lp.topMargin = dp(8);
                actions.addView(btn, lp);
                any = true;
            }
            if (any) panel.addView(actions, Qvk3Ui.match());

            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int maxW = dp(560);
            if (c.getResources().getDisplayMetrics().widthPixels > maxW) pl.width = maxW;
            outer.addView(panel, pl);
            outer.setOnClickListener(v -> {
                if (b.cancelable) cancel();
            });
            setView(outer, 0, 0, 0, 0);
        }

        /** Old-style inputs (platform underline) get the filled rounded field; switches and checkboxes get the accent. */
        private static void polish(Context c, Qvk3Ui.Palette p, View v) {
            if (v instanceof android.widget.EditText) {
                android.graphics.drawable.Drawable bg = v.getBackground();
                if (!(bg instanceof android.graphics.drawable.GradientDrawable)) field(c, p, (android.widget.EditText) v);
            } else if (v instanceof android.widget.CompoundButton) {
                android.widget.CompoundButton cb = (android.widget.CompoundButton) v;
                cb.setTextColor(p.text);
                android.content.res.ColorStateList tint = new android.content.res.ColorStateList(
                        new int[][]{{android.R.attr.state_checked}, {}}, new int[]{p.accent, p.text2});
                if (cb instanceof android.widget.Switch) {
                    ((android.widget.Switch) cb).setThumbTintList(tint);
                    ((android.widget.Switch) cb).setTrackTintList(tint);
                } else {
                    cb.setButtonTintList(tint);
                }
            } else if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) polish(c, p, g.getChildAt(i));
            }
        }

        private static boolean containsScroller(View v) {
            if (v instanceof ScrollView || v instanceof AbsListView) return true;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) if (containsScroller(g.getChildAt(i))) return true;
            }
            return false;
        }

        private static void detach(View v) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        }

        private View wrap(View body) {
            ScrollView sv = new ScrollView(c);
            sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(body);
            return sv;
        }

        /** "• a\n• b" → real list items with a hanging indent and accent bullets. */
        private CharSequence bullets(CharSequence text) {
            SpannableStringBuilder out = new SpannableStringBuilder();
            String[] lines = text.toString().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String t = line.trim();
                boolean bullet = t.startsWith("• ") || t.startsWith("- ") || t.startsWith("– ") || t.startsWith("● ") || t.startsWith("○ ");
                int start = out.length();
                out.append(bullet ? t.substring(2) : line);
                if (bullet) out.setSpan(new BulletSpan(dp(10), p.accent, dp(3)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (i < lines.length - 1) out.append('\n');
            }
            return out;
        }

        /** A choice row: title (+ subtitle after a line break), «BUG-0030 · …» gets a code chip; radio / switch for choices. */
        private View row(Qvk3Sheet.Builder b, int i) {
            String label = String.valueOf(b.items[i]);
            String sub = null;
            int nl = label.indexOf('\n');
            if (nl > 0) {
                sub = label.substring(nl + 1).trim();
                label = label.substring(0, nl).trim();
            }
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(dp(54));
            r.setPadding(dp(24), dp(10), dp(20), dp(10));
            r.setBackground(Qvk3Ui.ripple(c, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^((?:BUG|IDEA)-\\d+)\\s*·\\s*(.*)$").matcher(label);
            if (m.find()) {
                TextView chip = Qvk3Ui.text(c, m.group(1), 12, p.accent, true);
                chip.setPadding(dp(8), dp(2), dp(8), dp(2));
                chip.setBackground(Qvk3Ui.rounded((p.accent & 0x00FFFFFF) | 0x24000000, dp(8)));
                LinearLayout top = new LinearLayout(c);
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(chip);
                String rest = m.group(2);
                if (sub == null) {
                    sub = rest.isEmpty() ? null : rest;
                } else if (!rest.isEmpty()) {
                    TextView st = Qvk3Ui.text(c, rest, 13, p.text2, false);
                    st.setPadding(dp(8), 0, 0, 0);
                    top.addView(st);
                }
                texts.addView(top);
                if (sub != null) {
                    TextView t = Qvk3Ui.text(c, sub, 16, p.text, false);
                    t.setPadding(0, dp(4), 0, 0);
                    texts.addView(t);
                }
            } else {
                texts.addView(Qvk3Ui.text(c, label, 16, p.text, false));
                if (sub != null) {
                    TextView s = Qvk3Ui.text(c, sub, 13, p.text2, false);
                    s.setPadding(0, dp(2), 0, 0);
                    texts.addView(s);
                }
            }
            r.addView(texts, Qvk3Ui.weight(1));
            final int which = i;
            if (b.multi != null) {
                Qvk3Toggle tg = new Qvk3Toggle(c, b.multi[i], p.accent, p.dark ? 0xFF3A3B40 : 0xFFDDDEE3);
                r.addView(tg);
                r.setOnClickListener(v -> {
                    b.multi[which] = !b.multi[which];
                    tg.setChecked(b.multi[which], true);
                    if (b.multiL != null) b.multiL.onClick(this, which, b.multi[which]);
                });
            } else if (b.single) {
                View radio = new View(c) {
                    final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                    @Override
                    protected void onDraw(android.graphics.Canvas cv) {
                        float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = Math.min(cx, cy) - dp(1);
                        boolean on = b.checked == which;
                        pt.setStyle(android.graphics.Paint.Style.STROKE);
                        pt.setStrokeWidth(dp(2));
                        pt.setColor(on ? p.accent : p.text2);
                        cv.drawCircle(cx, cy, rad, pt);
                        if (on) {
                            pt.setStyle(android.graphics.Paint.Style.FILL);
                            cv.drawCircle(cx, cy, rad * 0.5f, pt);
                        }
                    }
                };
                r.addView(radio, new LinearLayout.LayoutParams(dp(22), dp(22)));
                r.setOnClickListener(v -> {
                    b.checked = which;
                    ViewGroup parent = (ViewGroup) r.getParent();
                    for (int k = 0; k < parent.getChildCount(); k++) parent.getChildAt(k).invalidate();
                    radio.invalidate();
                    if (b.itemsL != null) b.itemsL.onClick(this, which);
                });
            } else {
                android.widget.ImageView chev = new android.widget.ImageView(c);
                chev.setImageDrawable(Qvk3Icons.of(Qvk3Icons.RIGHT, (p.text2 & 0x00FFFFFF) | 0x99000000));
                r.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
                r.setOnClickListener(v -> {
                    dismiss();
                    if (b.itemsL != null) b.itemsL.onClick(this, which);
                });
            }
            r.setContentDescription(label + (sub == null ? "" : ". " + sub));
            return r;
        }

        private Button button(CharSequence label, boolean primary, boolean quiet) {
            Button btn = new Button(c);
            btn.setText(label);
            btn.setAllCaps(false);
            btn.setTextSize(16);
            btn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            btn.setStateListAnimator(null);
            btn.setMinHeight(0);
            btn.setMinimumHeight(0);
            btn.setPadding(dp(16), 0, dp(16), 0);
            int radius = dp(16);
            if (primary) {
                btn.setTextColor(Color.WHITE);
                btn.setBackground(Qvk3Ui.ripple(c, p.accent, 0x33FFFFFF, radius));
            } else if (quiet) {
                btn.setTextColor(p.text2);
                btn.setBackground(Qvk3Ui.ripple(c, 0, p.dark ? 0x22FFFFFF : 0x14000000, radius));
            } else {
                btn.setTextColor(p.accent);
                btn.setBackground(Qvk3Ui.ripple(c, (p.accent & 0x00FFFFFF) | 0x1F000000, (p.accent & 0x00FFFFFF) | 0x33000000, radius));
            }
            Qvk3Motion.press(btn);
            return btn;
        }

        @Override
        public Button getButton(int which) {
            int i = which == BUTTON_POSITIVE ? 0 : which == BUTTON_NEUTRAL ? 1 : which == BUTTON_NEGATIVE ? 2 : -1;
            if (i < 0) return super.getButton(which);
            if (buttons[i] == null) {
                // callers sometimes reach for a button that the dialog does not have: give them a detached one, never null
                buttons[i] = new Button(c);
                buttons[i].setVisibility(View.GONE);
            }
            return buttons[i];
        }

        @Override
        protected void onCreate(android.os.Bundle state) {
            super.onCreate(state);
            Window w = getWindow();
            if (w == null) return;
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // height by content and pinned to the bottom: the alert's own container wraps its content, so a full-height
            // window would leave the sheet at the top
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.45f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setWindowAnimations(0);
            View decor = w.getDecorView();
            decor.setPadding(0, 0, 0, 0);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.horizontalMargin = 0;
            lp.verticalMargin = 0;
            w.setAttributes(lp);
        }

        @Override
        protected void onStart() {
            super.onStart();
            if (panel == null) return;
            panel.setTranslationY(c.getResources().getDisplayMetrics().heightPixels * 0.4f);
            panel.setAlpha(0f);
            panel.animate().translationY(0f).alpha(1f).setDuration(280).setInterpolator(Qvk3Motion.OUT).start();
        }

        @Override
        public void dismiss() {
            if (closing || panel == null || !isShowing()) {
                super.dismiss();
                return;
            }
            closing = true;
            panel.animate().translationY(panel.getHeight() * 0.6f + dp(40)).alpha(0f).setDuration(180)
                    .withEndAction(() -> {
                        try {
                            super.dismiss();
                        } catch (Throwable ignored) {
                            // the window went away with its activity
                        }
                    }).start();
        }
    }
}
