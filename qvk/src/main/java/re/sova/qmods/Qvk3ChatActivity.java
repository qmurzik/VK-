package re.sova.qmods;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat screen of the QVK shell, iOS-messenger style: centred header with avatar, blue/grey bubbles with
 * grouped corners, photo messages, rounded composer. Text and photos are rendered natively; other attachment
 * kinds are shown as labels and open in the stock VK chat ("Открыть в ВК" in the header).
 */
public final class Qvk3ChatActivity extends Activity {
    private Qvk3Ios s;
    private long peer;
    private boolean group;
    private final List<Qvk3Messages.Msg> msgs = new ArrayList<>();
    private Adapter adapter;
    private ListView list;
    private EditText input;
    private ImageView send;
    private TextView status;
    private boolean loading, sending, loadingOlder, noOlder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            load(false);
            main.postDelayed(this, 4000);
        }
    };

    private int dp(float v) {
        return Qvk3Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        s = new Qvk3Ios(this);
        s.bars(this);
        peer = getIntent().getLongExtra("peer", 0);
        group = getIntent().getBooleanExtra("group", false);
        String title = String.valueOf(getIntent().getStringExtra("title"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(s.bg);
        root.setFitsSystemWindows(true);

        FrameLayout head = new FrameLayout(this);
        head.setBackgroundColor(s.bar);
        ImageView back = Qvk3Ui.icon(this, Qvk3Icons.BACK, s.tint, 48, 24, "Назад", v -> finish());
        head.addView(back, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL | Gravity.START));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.setPadding(0, dp(6), 0, dp(6));
        mid.setMinimumWidth(dp(170));
        mid.addView(s.avatar(this, title, getIntent().getStringExtra("photo"), 36, peer));
        TextView name = Qvk3Ui.line(this, title, 12, s.text, false);
        name.setGravity(Gravity.CENTER);
        name.setMaxWidth(dp(230));
        name.setMinWidth(dp(150));
        name.setAccessibilityHeading(true);
        mid.addView(name);
        head.addView(mid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        ImageView open = Qvk3Ui.icon(this, Qvk3Icons.MORE, s.tint, 48, 22, "Открыть этот чат в штатном ВКонтакте", v -> openStock());
        head.addView(open, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL | Gravity.END));
        root.addView(head, Qvk3Ui.match());
        View line = new View(this);
        line.setBackgroundColor(s.sep);
        root.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f))));

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setStackFromBottom(true);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        list.setClipToPadding(false);
        list.setPadding(0, dp(8), 0, dp(8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((p, v, i, id) -> {
            Qvk3Messages.Msg m = msgs.get(i);
            if (m.text.isEmpty()) return false;
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Сообщение", m.text));
            Qvk3Ui.toast(this, "Скопировано");
            return true;
        });
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        status = Qvk3Ui.text(this, "Загрузка…", 15, s.text2, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), 0, dp(24), 0);
        status.setOnClickListener(v -> load(true));
        body.addView(status, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setBackgroundColor(s.bar);
        composer.setPadding(dp(12), dp(7), dp(8), dp(7));
        input = new EditText(this);
        input.setHint("Сообщение");
        input.setTextSize(17);
        input.setMaxLines(5);
        input.setTextColor(s.text);
        input.setHintTextColor(s.text2);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(s.bg);
        ib.setCornerRadius(dp(19));
        ib.setStroke(Math.max(1, dp(0.7f)), s.sep);
        input.setBackground(ib);
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setMinHeight(dp(38));
        composer.addView(input, Qvk3Ui.weight(1));
        send = new ImageView(this);
        send.setImageDrawable(Qvk3Icons.of(Qvk3Icons.SEND, Color.WHITE));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setShape(GradientDrawable.OVAL);
        sbg.setColor(s.tint);
        send.setBackground(sbg);
        send.setPadding(dp(8), dp(8), dp(8), dp(8));
        send.setContentDescription("Отправить");
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(36), dp(36));
        sl.leftMargin = dp(8);
        sl.bottomMargin = dp(1);
        composer.addView(send, sl);
        root.addView(composer, Qvk3Ui.match());
        setContentView(root);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                sendState();
            }
        });
        sendState();
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView v, int st) {
            }

            @Override
            public void onScroll(android.widget.AbsListView v, int first, int visible, int total) {
                if (first == 0 && total > 0 && !msgs.isEmpty()) older();
            }
        });
    }

    private void sendState() {
        boolean can = input.getText().toString().trim().length() > 0 && !sending;
        send.setAlpha(can ? 1f : 0.35f);
        send.setEnabled(can);
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(poll);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(poll); // no background polling
        super.onPause();
    }

    private void openStock() {
        if (!RuntimeBridge.navigateChecked(this, "im?sel=" + (peer > 2000000000L ? "c" + (peer - 2000000000L) : String.valueOf(peer)))) {
            Qvk3Ui.toast(this, "Не удалось открыть чат в ВКонтакте");
        }
    }

    private void load(boolean showErrors) {
        if (loading || peer == 0) return;
        loading = true;
        Qvk3Jobs.io(() -> {
            List<Qvk3Messages.Msg> r = null;
            String err = null;
            try {
                r = Qvk3Messages.history(peer, 0);
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final List<Qvk3Messages.Msg> fr = r;
            final String fe = err;
            main.post(() -> {
                loading = false;
                if (isDestroyed()) return;
                if (fr == null) {
                    if (msgs.isEmpty()) status.setText("Не удалось загрузить переписку: " + fe + "\nНажмите, чтобы повторить");
                    return;
                }
                long lastOld = msgs.isEmpty() ? -1 : msgs.get(msgs.size() - 1).id;
                long lastNew = fr.isEmpty() ? -1 : fr.get(fr.size() - 1).id;
                boolean first = msgs.isEmpty();
                if (first || lastOld != lastNew) {
                    // keep older pages already loaded above the newest 50
                    long oldestNew = fr.isEmpty() ? Long.MAX_VALUE : fr.get(0).id;
                    List<Qvk3Messages.Msg> merged = new ArrayList<>();
                    for (Qvk3Messages.Msg m : msgs) if (m.id < oldestNew) merged.add(m);
                    merged.addAll(fr);
                    msgs.clear();
                    msgs.addAll(merged);
                    adapter.notifyDataSetChanged();
                    if (first) list.setSelection(msgs.size() - 1);
                    Qvk3Jobs.io(() -> Qvk3Messages.markRead(peer));
                }
                status.setVisibility(msgs.isEmpty() ? View.VISIBLE : View.GONE);
                status.setText("Сообщений пока нет");
            });
        });
    }

    private void older() {
        if (loadingOlder || noOlder || msgs.size() < 50) return;
        loadingOlder = true;
        final int offset = msgs.size();
        Qvk3Jobs.io(() -> {
            List<Qvk3Messages.Msg> r = new ArrayList<>();
            try {
                r = Qvk3Messages.history(peer, offset);
            } catch (Exception ignored) {
            }
            final List<Qvk3Messages.Msg> fr = r;
            main.post(() -> {
                loadingOlder = false;
                if (fr.isEmpty()) {
                    noOlder = true;
                    return;
                }
                long oldest = msgs.get(0).id;
                List<Qvk3Messages.Msg> add = new ArrayList<>();
                for (Qvk3Messages.Msg m : fr) if (m.id < oldest) add.add(m);
                int pos = list.getFirstVisiblePosition();
                View top = list.getChildAt(0);
                int y = top == null ? 0 : top.getTop();
                msgs.addAll(0, add);
                adapter.notifyDataSetChanged();
                list.setSelectionFromTop(pos + add.size(), y); // keep the reading position
            });
        });
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty() || sending) return;
        sending = true;
        sendState();
        Qvk3Jobs.io(() -> {
            String err = null;
            try {
                Qvk3Messages.send(peer, text);
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final String fe = err;
            main.post(() -> {
                sending = false;
                if (fe == null) {
                    input.setText("");
                    load(false);
                } else {
                    Qvk3Ui.toast(this, "Не отправлено: " + fe); // the text stays in the field
                }
                sendState();
            });
        });
    }

    final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return msgs.size();
        }

        @Override
        public Object getItem(int i) {
            return msgs.get(i);
        }

        @Override
        public long getItemId(int i) {
            return msgs.get(i).id;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Activity a = Qvk3ChatActivity.this;
            Qvk3Messages.Msg m = msgs.get(i);
            Qvk3Messages.Msg prev = i > 0 ? msgs.get(i - 1) : null, next = i + 1 < msgs.size() ? msgs.get(i + 1) : null;
            boolean firstOfRun = prev == null || prev.from != m.from || m.date - prev.date > 600;
            boolean lastOfRun = next == null || next.from != m.from || next.date - m.date > 600;
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(10), dp(firstOfRun ? 8 : 1.5f), dp(10), 0);
            if (prev == null || m.date - prev.date > 3600) {
                TextView day = Qvk3Ui.text(a, Qvk3Messages.time(m.date), 12, s.text2, false);
                day.setGravity(Gravity.CENTER);
                day.setPadding(0, dp(6), 0, dp(8));
                col.addView(day, Qvk3Ui.match());
            }
            if (group && !m.out && firstOfRun && !m.sender.isEmpty()) {
                TextView who = Qvk3Ui.line(a, m.sender, 12, s.text2, false);
                who.setPadding(dp(14), 0, 0, dp(2));
                col.addView(who);
            }
            float big = dp(19), small = dp(5);
            // iOS grouping: the corner next to the neighbouring bubble of the same sender is tight
            float tl = m.out ? big : (firstOfRun ? big : small), bl = m.out ? big : (lastOfRun ? big : small);
            float tr = m.out ? (firstOfRun ? big : small) : big, br = m.out ? (lastOfRun ? big : small) : big;
            float[] radii = {tl, tl, tr, tr, br, br, bl, bl};
            View bubble;
            int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.76f);
            if (!m.photo.isEmpty()) {
                ImageView iv = new ImageView(a);
                int w = Math.min(maxW, dp(250)), h = m.photoW > 0 ? Math.max(dp(80), Math.min(dp(340), w * m.photoH / m.photoW)) : w;
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setBackground(Qvk3Ui.rounded(s.bubbleIn, big));
                iv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
                iv.setContentDescription("Фотография");
                final String url = m.photo;
                iv.setTag(url);
                Qvk3Images.get(a).load(url, 600, b -> {
                    if (b != null && url.equals(iv.getTag())) iv.setImageDrawable(Qvk3Ui.roundedBitmap(b, big));
                });
                bubble = iv;
            } else {
                TextView t = Qvk3Ui.text(a, m.text, 17, m.out ? Color.WHITE : s.text, false);
                t.setMaxWidth(maxW);
                t.setPadding(dp(13), dp(8), dp(13), dp(8));
                GradientDrawable g = new GradientDrawable();
                g.setColor(m.out ? s.tint : s.bubbleIn);
                g.setCornerRadii(radii);
                t.setBackground(g);
                t.setLinkTextColor(m.out ? Color.WHITE : s.tint);
                android.text.util.Linkify.addLinks(t, android.text.util.Linkify.WEB_URLS);
                t.setMovementMethod(null); // keep row long-press (copy) working; links open via the stock chat
                bubble = t;
            }
            LinearLayout line = new LinearLayout(a);
            line.setGravity(m.out ? Gravity.END : Gravity.START);
            line.addView(bubble);
            col.addView(line, Qvk3Ui.match());
            col.setContentDescription((m.out ? "Вы: " : (m.sender.isEmpty() ? "" : m.sender + ": ")) + (m.photo.isEmpty() ? m.text : "фотография") + ", " + Qvk3Messages.time(m.date));
            return col;
        }
    }
}
