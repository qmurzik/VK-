package re.sova.qmods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.media.MediaPlayer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One chat of the QVK messenger. Hosted by VK's own chat screen (its fragment returns this view) or by
 * {@link Qvk3ImChatActivity}; {@code onClose} leaves the screen the way the host does.
 */
final class Qvk3ImChatView extends LinearLayout implements Qvk3ImStore.Listener, Qvk3ImBubble.Host {
    final Activity a;
    final long peer;
    private final Qvk3ImStore store;
    private final Qvk3ImUi.Look look;
    private final Qvk3ImBubble bubbles;
    private final Runnable onClose;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final TextView title, status, pinnedText;
    private final ImageView avatar;
    private final View pinnedBar;
    private final EditText input;
    private final ImageView send;
    private final LinearLayout contextBar;
    private final TextView contextTitle, contextText;
    private final TextView cantWrite;
    private final TextView toBottom;
    private boolean built;
    private final LinearLayout selectionBar;
    private final TextView selectionCount;
    private boolean loadingOlder, firstLoad = true;
    private long replyTo;
    private Qvk3ImModel.Message editing;
    private final List<Long> forwarding = new ArrayList<>();
    private long forwardFrom;
    private final Set<Long> selected = new LinkedHashSet<>();
    private long lastTyping;

    Qvk3ImChatView(Activity a, long peer, Runnable onClose) {
        super(a);
        this.a = a;
        this.peer = peer;
        this.onClose = onClose;
        this.store = Qvk3ImStore.get(a);
        this.look = new Qvk3ImUi.Look(a);
        this.bubbles = new Qvk3ImBubble(a, look, this);
        Qvk3Ui.Palette p = look.p;
        setOrientation(VERTICAL);
        setBackgroundColor(p.bg);

        // header
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), dp(6), dp(4), dp(6));
        head.setBackgroundColor(p.surface);
        head.setElevation(dp(1));
        head.addView(Qvk3Ui.icon(a, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> close()));
        avatar = new ImageView(a);
        head.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout tt = new LinearLayout(a);
        tt.setOrientation(VERTICAL);
        tt.setPadding(dp(10), 0, 0, 0);
        title = Qvk3Ui.line(a, "", 17, p.text, true);
        status = Qvk3Ui.line(a, "", 13, p.text2, false);
        tt.addView(title);
        tt.addView(status);
        head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        // the name and avatar open the chat info (members, attachments, invite link…)
        View.OnClickListener info = v -> Qvk3ImInfo.show(a, peer, this::searchInChat);
        tt.setOnClickListener(info);
        avatar.setOnClickListener(info);
        tt.setContentDescription("Информация о чате");
        head.addView(Qvk3Ui.icon(a, Qvk3Icons.SEARCH, p.text, 44, 22, "Поиск в чате", v -> searchInChat()));
        head.addView(Qvk3Ui.icon(a, Qvk3Icons.MORE, p.text, 44, 22, "Ещё", v -> menu()));
        addView(head, Qvk3Ui.match());

        // selection bar (replaces the header actions while messages are selected)
        selectionBar = new LinearLayout(a);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setPadding(dp(4), dp(6), dp(4), dp(6));
        selectionBar.setBackgroundColor(p.surface);
        selectionBar.addView(Qvk3Ui.icon(a, Qvk3Icons.CLOSE, p.text, 48, 22, "Отменить выбор", v -> clearSelection()));
        selectionCount = Qvk3Ui.line(a, "", 17, p.text, true);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(0, -2, 1f));
        selectionBar.addView(Qvk3Ui.icon(a, Qvk3Icons.SEND, p.text, 44, 22, "Переслать", v -> forwardSelected()));
        selectionBar.addView(Qvk3Ui.icon(a, Qvk3Icons.LIST, p.text, 44, 22, "Копировать", v -> copySelected()));
        selectionBar.addView(Qvk3Ui.icon(a, Qvk3Icons.CLOSE, 0xFFE64646, 44, 22, "Удалить", v -> deleteSelected()));
        selectionBar.setVisibility(GONE);
        addView(selectionBar, Qvk3Ui.match());

        // pinned message
        LinearLayout pin = new LinearLayout(a);
        pin.setGravity(Gravity.CENTER_VERTICAL);
        pin.setPadding(dp(14), dp(6), dp(14), dp(6));
        pin.setBackgroundColor(p.surface);
        pin.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(p.surface), new Qvk3ImBubble.Bar(p.accent, dp(3))}));
        LinearLayout pinTexts = new LinearLayout(a);
        pinTexts.setOrientation(VERTICAL);
        pinTexts.setPadding(dp(10), 0, 0, 0);
        pinTexts.addView(Qvk3Ui.line(a, "Закреплённое сообщение", 13, p.accent, true));
        pinnedText = Qvk3Ui.line(a, "", 14, p.text, false);
        pinTexts.addView(pinnedText);
        pin.addView(pinTexts, new LinearLayout.LayoutParams(0, -2, 1f));
        pin.setOnClickListener(v -> {
            Qvk3ImModel.Conversation c = store.conversation(peer);
            if (c != null && c.pinnedMessage != null) jumpTo(c.pinnedMessage);
        });
        pinnedBar = pin;
        pin.setVisibility(GONE);
        addView(pin, Qvk3Ui.match());

        // messages
        FrameLayout box = new FrameLayout(a);
        list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setStackFromBottom(true);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(6));
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {
            }

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {
                // BUG-0051/0052: Android 16 calls onScroll inside setOnScrollListener, before the rest is built
                if (!built) return;
                if (first <= 3 && total > 0 && !firstLoad) loadOlder();
                boolean atBottom = first + visible >= total - 1;
                toBottom.setVisibility(atBottom ? GONE : VISIBLE);
                if (atBottom) markRead();
            }
        });
        box.addView(list, new FrameLayout.LayoutParams(-1, -1));
        toBottom = Qvk3Ui.text(a, "↓", 18, p.text, true);
        toBottom.setGravity(Gravity.CENTER);
        toBottom.setBackground(Qvk3Ui.rounded(p.surface, dp(22)));
        toBottom.setElevation(dp(4));
        toBottom.setContentDescription("Вниз, к новым сообщениям");
        toBottom.setOnClickListener(v -> list.smoothScrollToPosition(Math.max(0, adapter.getCount() - 1)));
        toBottom.setVisibility(GONE);
        FrameLayout.LayoutParams tbl = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM | Gravity.END);
        tbl.setMargins(0, 0, dp(12), dp(12));
        box.addView(toBottom, tbl);
        addView(box, new LinearLayout.LayoutParams(-1, 0, 1f));

        // reply / edit / forward bar
        contextBar = new LinearLayout(a);
        contextBar.setGravity(Gravity.CENTER_VERTICAL);
        contextBar.setPadding(dp(14), dp(6), dp(4), dp(2));
        contextBar.setBackgroundColor(p.surface);
        LinearLayout ct = new LinearLayout(a);
        ct.setOrientation(VERTICAL);
        ct.setBackground(new Qvk3ImBubble.Bar(p.accent, dp(2)));
        ct.setPadding(dp(10), 0, 0, 0);
        contextTitle = Qvk3Ui.line(a, "", 13, p.accent, true);
        contextText = Qvk3Ui.line(a, "", 14, p.text, false);
        ct.addView(contextTitle);
        ct.addView(contextText);
        contextBar.addView(ct, new LinearLayout.LayoutParams(0, -2, 1f));
        contextBar.addView(Qvk3Ui.icon(a, Qvk3Icons.CLOSE, p.text2, 40, 18, "Отменить", v -> clearContext()));
        contextBar.setVisibility(GONE);
        addView(contextBar, Qvk3Ui.match());

        // composer
        LinearLayout composer = new LinearLayout(a);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(dp(6), dp(6), dp(6), dp(8));
        composer.setBackgroundColor(p.surface);
        composer.addView(Qvk3Ui.icon(a, Qvk3Icons.PLUS, p.text2, 44, 24, "Прикрепить", v -> Qvk3ImAttach.pick(this)));
        input = new EditText(a);
        input.setHint("Сообщение");
        input.setTextSize(16);
        input.setTextColor(p.text);
        input.setHintTextColor(p.text2);
        input.setMaxLines(6);
        input.setMinHeight(dp(42));
        input.setPadding(dp(14), dp(9), dp(14), dp(9));
        input.setBackground(Qvk3Ui.rounded(p.dark ? 0x14FFFFFF : 0x0D000000, dp(21)));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                updateSend();
                Qvk3ImDrafts.save(a, peer, editing == null ? e.toString() : "");
                long now = System.currentTimeMillis();
                if (e.length() > 0 && now - lastTyping > 5000) {
                    lastTyping = now;
                    Qvk3Jobs.io(() -> Qvk3ImApi.typing(peer)); // NetworkBridge drops it when «непечаталка» is on
                }
            }
        });
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(0, -2, 1f);
        il.setMargins(dp(2), 0, dp(4), 0);
        composer.addView(input, il);
        composer.addView(Qvk3Ui.icon(a, Qvk3Icons.GRID, p.text2, 44, 22, "Стикеры", v -> Qvk3ImStickers.open(this)));
        send = Qvk3Ui.icon(a, Qvk3Icons.SEND, p.accent, 44, 24, "Отправить", v -> send());
        composer.addView(send);
        addView(composer, Qvk3Ui.match());
        cantWrite = Qvk3Ui.text(a, "", 14, p.text2, false);
        cantWrite.setGravity(Gravity.CENTER);
        cantWrite.setPadding(dp(16), dp(14), dp(16), dp(14));
        cantWrite.setBackgroundColor(p.surface);
        cantWrite.setVisibility(GONE);
        addView(cantWrite, Qvk3Ui.match());
        composerHolder = composer;
        built = true;

        String draft = Qvk3ImDrafts.get(a, peer);
        if (!draft.isEmpty()) {
            input.setText(draft);
            input.setSelection(draft.length());
        }
        updateSend();
        changed(peer);
    }

    private final View composerHolder;

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        store.addListener(this);
        Qvk3ImReactions.load(a, () -> adapter.notifyDataSetChanged());
        if (store.conversation(peer) == null) store.loadConversation(peer);
        store.loadHistory(peer, false, err -> {
            firstLoad = false;
            if (err != null && store.messages(peer).isEmpty()) Qvk3Ui.toast(a, "Не удалось загрузить сообщения: " + err);
            markRead();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        store.removeListener(this);
        Qvk3ImVoice.stop();
        super.onDetachedFromWindow();
    }

    void close() {
        Qvk3ImUi.hideKeyboard(input);
        if (onClose != null) onClose.run();
    }

    /** Back: selection → context (reply/edit) → leave. */
    boolean back() {
        if (!selected.isEmpty()) {
            clearSelection();
            return true;
        }
        if (contextBar.getVisibility() == VISIBLE) {
            clearContext();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ store

    @Override
    public void changed(long p) {
        if (p != peer && p != 0) return;
        Qvk3ImModel.Conversation c = store.conversation(peer);
        if (c != null) {
            title.setText(c.title);
            Qvk3ImUi.avatar(avatar, c.photo, c.title, 40);
            String st = Qvk3ImUi.status(store, c);
            status.setText(st);
            status.setTextColor(st.endsWith("…") || st.startsWith("в сети") ? look.p.accent : look.p.text2);
            boolean pinned = c.pinnedMessage != null;
            pinnedBar.setVisibility(pinned ? VISIBLE : GONE);
            if (pinned) pinnedText.setText(Qvk3ImModel.preview(c.pinnedMessage, store.people));
            cantWrite.setVisibility(c.canWrite ? GONE : VISIBLE);
            cantWrite.setText(c.cantWriteReason);
            composerHolder.setVisibility(c.canWrite ? VISIBLE : GONE);
        }
        if (p == peer) adapter.notifyDataSetChanged();
    }

    private void loadOlder() {
        if (loadingOlder || store.historyComplete.contains(peer)) return;
        loadingOlder = true;
        int before = adapter.getCount();
        int first = list.getFirstVisiblePosition();
        View top = list.getChildAt(0);
        int offset = top == null ? 0 : top.getTop();
        store.loadHistory(peer, true, err -> {
            loadingOlder = false;
            int added = adapter.getCount() - before;
            if (added > 0) list.setSelectionFromTop(first + added, offset); // keep the reading position
        });
    }

    private long lastMarked;

    private void markRead() {
        Qvk3ImModel.Conversation c = store.conversation(peer);
        if (c == null || c.unread == 0 || c.last == null || c.last.cmid == lastMarked) return;
        lastMarked = c.last.cmid;
        long cmid = c.last.cmid;
        Qvk3Jobs.io(() -> {
            try {
                Qvk3ImApi.markRead(peer);
                Qvk3Jobs.main(() -> store.read(peer, cmid, false));
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------------ Bubble host

    @Override
    public Map<Long, Qvk3ImModel.Person> people() {
        return store.people;
    }

    @Override
    public long outRead() {
        Qvk3ImModel.Conversation c = store.conversation(peer);
        return c == null ? 0 : c.outRead;
    }

    @Override
    public String reactionUrl(int id) {
        return Qvk3ImReactions.url(id);
    }

    @Override
    public void openPhoto(List<Qvk3ImModel.Attachment> photos, int index) {
        Qvk3ImViewer.show(a, photos, index);
    }

    @Override
    public void openAttachment(Qvk3ImModel.Attachment x) {
        switch (x.type) {
            case "audio":
                try {
                    JSONObject o = x.raw.optJSONObject("audio");
                    Qvk3Track t = Qvk3Track.fromVk(o);
                    Qvk3MusicEngine.get(a).play(Collections.singletonList(t), 0);
                } catch (Exception e) {
                    Qvk3Ui.toast(a, "Не удалось включить трек");
                }
                break;
            case "doc":
            case "link":
            case "geo":
                if (x.url.startsWith("geo:")) {
                    try {
                        a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(x.url)));
                    } catch (Exception e) {
                        Qvk3Ui.toast(a, "Нет приложения карт");
                    }
                } else Qvk3ImUi.openLink(a, x.url);
                break;
            case "video":
            case "wall":
                Qvk3ImUi.openLink(a, x.url);
                break;
            case "poll":
                pollDialog(x);
                break;
            case "video_message":
                Qvk3ImCircle.play(a, x);
                break;
            case "story":
                if (x.ownerId != 0 || x.id != 0) Qvk3ImUi.openLink(a, "https://vk.com/story" + x.ownerId + "_" + x.id);
                else Qvk3ImEntry.openStock(a, peer);
                break;
            case "market":
                if (x.ownerId != 0 || x.id != 0) Qvk3ImUi.openLink(a, "https://vk.com/product" + x.ownerId + "_" + x.id);
                else Qvk3ImEntry.openStock(a, peer);
                break;
            default:
                Qvk3ImEntry.openStock(a, peer); // gifts, money transfers…: VK's own screen shows them fully
        }
    }

    /** Polls right in QVK: results with percentages, your vote marked; vote (one or several answers) or take it back. */
    private void pollDialog(Qvk3ImModel.Attachment x) {
        JSONObject poll = x.raw == null ? null : x.raw.optJSONObject("poll");
        if (poll == null) {
            Qvk3ImEntry.openStock(a, peer);
            return;
        }
        org.json.JSONArray answers = poll.optJSONArray("answers");
        org.json.JSONArray mine = poll.optJSONArray("answer_ids");
        java.util.Set<Long> voted = new java.util.HashSet<>();
        for (int i = 0; mine != null && i < mine.length(); i++) voted.add(mine.optLong(i));
        boolean closed = poll.optBoolean("closed") || (poll.optLong("end_date") > 0 && poll.optLong("end_date") * 1000 < System.currentTimeMillis());
        boolean multiple = poll.optBoolean("multiple");
        int n = answers == null ? 0 : answers.length();
        String[] labels = new String[n];
        long[] ids = new long[n];
        for (int i = 0; i < n; i++) {
            JSONObject o = answers.optJSONObject(i);
            ids[i] = o.optLong("id");
            labels[i] = (voted.contains(ids[i]) ? "✓ " : "") + o.optString("text")
                    + (voted.isEmpty() && !closed ? "" : "  ·  " + Math.round(o.optDouble("rate", 0)) + "% (" + o.optInt("votes") + ")");
        }
        long owner = poll.optLong("owner_id"), id = poll.optLong("id");
        String title = poll.optString("question") + (poll.optBoolean("anonymous") ? "\nАнонимный опрос" : "")
                + " · голосов: " + poll.optInt("votes") + (closed ? " · завершён" : "");
        android.app.AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle(title);
        if (!voted.isEmpty() || closed) {
            b.setItems(labels, null);
            if (!closed && poll.optBoolean("can_vote", true)) b.setNeutralButton("Отменить голос", (d, w) -> simple(() ->
                    Qvk3ImApi.call("polls.deleteVote", "owner_id", Long.toString(owner), "poll_id", Long.toString(id)), "Голос отменён", null));
            b.setPositiveButton("Закрыть", null).show();
            return;
        }
        if (multiple) {
            boolean[] checked = new boolean[n];
            b.setMultiChoiceItems(labels, checked, (d, w, on) -> checked[w] = on)
                    .setPositiveButton("Голосовать", (d, w) -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) if (checked[i]) sb.append(sb.length() == 0 ? "" : ",").append(ids[i]);
                        if (sb.length() == 0) return;
                        vote(owner, id, sb.toString());
                    }).setNegativeButton("Отмена", null).show();
        } else {
            b.setItems(labels, (d, w) -> vote(owner, id, Long.toString(ids[w]))).setNegativeButton("Отмена", null).show();
        }
    }

    private void vote(long owner, long pollId, String answers) {
        simple(() -> Qvk3ImApi.call("polls.addVote", "owner_id", Long.toString(owner), "poll_id", Long.toString(pollId), "answer_ids", answers),
                "Голос учтён", () -> store.loadHistory(peer, false, err -> { }));
    }

    @Override
    public void playVoice(Qvk3ImModel.Message m, Qvk3ImModel.Attachment x, Qvk3ImBubble.Wave view) {
        Qvk3ImVoice.toggle(a, x.url, view);
    }

    @Override
    public void jumpTo(Qvk3ImModel.Message target) {
        List<Row> rows = adapter.rows;
        for (int i = 0; i < rows.size(); i++) {
            Qvk3ImModel.Message m = rows.get(i).msg;
            if (m != null && ((target.id != 0 && m.id == target.id) || (target.cmid != 0 && m.cmid == target.cmid))) {
                list.setSelectionFromTop(i, dp(80));
                flash = m.id;
                adapter.notifyDataSetChanged();
                postDelayed(() -> {
                    flash = 0;
                    adapter.notifyDataSetChanged();
                }, 1200);
                return;
            }
        }
        Qvk3Ui.toast(a, "Сообщение выше — пролистайте историю");
    }

    private long flash;

    @Override
    public void react(Qvk3ImModel.Message m, int reaction) {
        if (m.cmid == 0) return;
        int old = m.myReaction;
        applyReaction(m, reaction);
        Qvk3Jobs.io(() -> {
            try {
                Qvk3ImApi.react(peer, m.cmid, reaction);
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    applyReaction(m, old);
                    Qvk3Ui.toast(a, "Реакция не поставлена: " + Qvk3Jobs.message(e));
                });
            }
        });
    }

    /** Optimistic local change of the reaction counters. */
    private void applyReaction(Qvk3ImModel.Message m, int reaction) {
        List<Qvk3ImModel.Reaction> next = new ArrayList<>();
        boolean added = false;
        for (Qvk3ImModel.Reaction r : m.reactions) {
            int count = r.count - (r.mine ? 1 : 0) + (r.id == reaction ? 1 : 0);
            if (r.id == reaction) added = true;
            if (count > 0) next.add(newReaction(r.id, count, r.id == reaction));
        }
        if (!added && reaction != 0) next.add(newReaction(reaction, 1, true));
        m.reactions.clear();
        m.reactions.addAll(next);
        m.myReaction = reaction;
        adapter.notifyDataSetChanged();
    }

    private static Qvk3ImModel.Reaction newReaction(int id, int count, boolean mine) {
        try {
            JSONObject o = new JSONObject().put("reaction_id", mine ? id : 0)
                    .put("reactions", new JSONArray().put(new JSONObject().put("reaction_id", id).put("count", count)));
            return Qvk3ImModel.message(o).reactions.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ message actions

    void tapMessage(Qvk3ImModel.Message m) {
        if (!selected.isEmpty()) {
            toggleSelect(m);
            return;
        }
        if (m.local == 2) {
            retry(m);
            return;
        }
        if (m.service() || m.local != 0) return;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        Qvk3ImModel.Conversation c = store.conversation(peer);
        boolean canWrite = c == null || c.canWrite;
        if (canWrite) {
            items.add("Ответить");
            acts.add(() -> reply(m));
        }
        items.add("Переслать");
        acts.add(() -> forward(Collections.singletonList(m.id)));
        if (!m.text.isEmpty()) {
            items.add("Скопировать текст");
            acts.add(() -> copy(m.text));
        }
        long now = System.currentTimeMillis() / 1000;
        if (m.out && now - m.date < 24 * 3600 && canWrite) {
            items.add("Изменить");
            acts.add(() -> edit(m));
        }
        if (c != null && (c.chat() || !c.chat())) {
            items.add("Закрепить");
            acts.add(() -> simple(() -> Qvk3ImApi.pin(peer, m.id), "Сообщение закреплено", () -> store.loadConversation(peer)));
        }
        items.add(m.important ? "Убрать из важных" : "Отметить как важное");
        acts.add(() -> simple(() -> Qvk3ImApi.important(m.id, !m.important), null, () -> {
            m.important = !m.important;
            adapter.notifyDataSetChanged();
        }));
        items.add("Выбрать\nОтметить несколько сообщений");
        acts.add(() -> toggleSelect(m));
        items.add("Удалить");
        acts.add(() -> delete(Collections.singletonList(m)));
        android.app.AlertDialog.Builder b = Qvk3Ui.dialog(a).setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run());
        View reactions = reactionPicker(m);
        if (reactions != null) b.setView(reactions);
        b.show();
    }

    /** A row of VK reactions above the actions (tap = set / remove). */
    private View reactionPicker(Qvk3ImModel.Message m) {
        List<Integer> ids = Qvk3ImReactions.ids();
        if (ids.isEmpty() || m.cmid == 0) return null;
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setPadding(dp(18), 0, dp(18), dp(8));
        for (Integer id : ids) {
            ImageView iv = new ImageView(a);
            iv.setPadding(dp(6), dp(6), dp(6), dp(6));
            if (id == m.myReaction) iv.setBackground(Qvk3Ui.rounded((look.accent & 0x00FFFFFF) | 0x33000000, dp(22)));
            Qvk3Images.get(a).load(Qvk3ImReactions.url(id), 96, bm -> {
                if (bm != null) iv.setImageBitmap(bm);
            });
            iv.setOnClickListener(v -> {
                react(m, id == m.myReaction ? 0 : id);
                android.app.Dialog d = Qvk3Sheet.lastShown;
                if (d != null && d.isShowing()) d.dismiss();
            });
            row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        hs.addView(row);
        return hs;
    }

    void longPress(Qvk3ImModel.Message m) {
        if (m.service() || m.local != 0) return;
        toggleSelect(m);
    }

    private void toggleSelect(Qvk3ImModel.Message m) {
        if (!selected.remove(m.id)) selected.add(m.id);
        selectionBar.setVisibility(selected.isEmpty() ? GONE : VISIBLE);
        selectionCount.setText(Qvk3SettingsActivity.plural(selected.size(), "сообщение", "сообщения", "сообщений"));
        adapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        selected.clear();
        selectionBar.setVisibility(GONE);
        adapter.notifyDataSetChanged();
    }

    private List<Qvk3ImModel.Message> selectedMessages() {
        List<Qvk3ImModel.Message> out = new ArrayList<>();
        for (Qvk3ImModel.Message m : store.messages(peer)) if (selected.contains(m.id)) out.add(m);
        return out;
    }

    private void forwardSelected() {
        List<Long> ids = new ArrayList<>(selected);
        clearSelection();
        forward(ids);
    }

    private void copySelected() {
        StringBuilder b = new StringBuilder();
        for (Qvk3ImModel.Message m : selectedMessages()) {
            Qvk3ImModel.Person p = store.people.get(m.from);
            b.append(p == null ? "" : p.name + ", ").append(Qvk3ImUi.clock(m.date)).append(":\n").append(m.text).append("\n\n");
        }
        copy(b.toString().trim());
        clearSelection();
    }

    private void deleteSelected() {
        List<Qvk3ImModel.Message> ms = selectedMessages();
        clearSelection();
        delete(ms);
    }

    private void copy(String text) {
        ClipboardManager cm = a.getSystemService(ClipboardManager.class);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Сообщение", text));
        Qvk3Ui.toast(a, "Скопировано");
    }

    private void reply(Qvk3ImModel.Message m) {
        editing = null;
        forwarding.clear();
        replyTo = m.id;
        Qvk3ImModel.Person p = store.people.get(m.from);
        showContext("Ответ " + (p == null ? "" : p.first), Qvk3ImModel.preview(m, store.people));
        Qvk3ImUi.showKeyboard(input);
    }

    private void edit(Qvk3ImModel.Message m) {
        replyTo = 0;
        forwarding.clear();
        editing = m;
        showContext("Редактирование", Qvk3ImModel.preview(m, store.people));
        input.setText(m.text);
        input.setSelection(input.length());
        Qvk3ImUi.showKeyboard(input);
    }

    /** Forward: pick a chat (the conversation list) — or attach to this chat's composer when started from another chat. */
    private void forward(List<Long> ids) {
        List<Qvk3ImModel.Conversation> cs = new ArrayList<>(store.conversations);
        String[] labels = new String[Math.min(cs.size(), 60)];
        for (int i = 0; i < labels.length; i++) labels[i] = cs.get(i).title + "\n" + (cs.get(i).chat() ? "Беседа" : "Диалог");
        Qvk3Ui.dialog(a).setTitle("Переслать в…").setItems(labels, (d, w) -> {
            Qvk3ImModel.Conversation to = cs.get(w);
            if (to.peer == peer) {
                forwarding.clear();
                forwarding.addAll(ids);
                forwardFrom = peer;
                replyTo = 0;
                editing = null;
                showContext("Пересылка", Qvk3SettingsActivity.plural(ids.size(), "сообщение", "сообщения", "сообщений"));
                updateSend();
                return;
            }
            Qvk3Jobs.io(() -> {
                try {
                    Qvk3ImApi.forward(to.peer, peer, ids, "");
                    Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Переслано в «" + to.title + "»"));
                } catch (Exception e) {
                    Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось переслать: " + Qvk3Jobs.message(e)));
                }
            });
        }).setNegativeButton("Отмена", null).show();
    }

    private void delete(List<Qvk3ImModel.Message> ms) {
        if (ms.isEmpty()) return;
        long now = System.currentTimeMillis() / 1000;
        boolean allMine = true;
        for (Qvk3ImModel.Message m : ms) if (!m.out || now - m.date > 24 * 3600) allMine = false;
        List<Long> ids = new ArrayList<>();
        Set<Long> cmids = new HashSet<>();
        for (Qvk3ImModel.Message m : ms) {
            ids.add(m.id);
            cmids.add(m.cmid);
        }
        android.app.AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle("Удалить " + Qvk3SettingsActivity.plural(ms.size(), "сообщение", "сообщения", "сообщений") + "?");
        if (allMine) b.setPositiveButton("Удалить для всех", (d, w) -> doDelete(ids, cmids, true));
        b.setNeutralButton("Удалить у меня", (d, w) -> doDelete(ids, cmids, false));
        b.setNegativeButton("Отмена", null).show();
    }

    private void doDelete(List<Long> ids, Set<Long> cmids, boolean forAll) {
        Qvk3Jobs.io(() -> {
            try {
                Qvk3ImApi.delete(peer, ids, forAll);
                Qvk3Jobs.main(() -> store.removeMessages(peer, cmids));
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось удалить: " + Qvk3Jobs.message(e)));
            }
        });
    }

    interface Call {
        void run() throws Exception;
    }

    private void simple(Call call, String ok, Runnable after) {
        Qvk3Jobs.io(() -> {
            try {
                call.run();
                Qvk3Jobs.main(() -> {
                    if (ok != null) Qvk3Ui.toast(a, ok);
                    if (after != null) after.run();
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось: " + Qvk3Jobs.message(e)));
            }
        });
    }

    private void showContext(String t, String text) {
        contextTitle.setText(t);
        contextText.setText(text);
        contextBar.setVisibility(VISIBLE);
    }

    private void clearContext() {
        if (editing != null) input.setText(Qvk3ImDrafts.get(a, peer));
        replyTo = 0;
        editing = null;
        forwarding.clear();
        contextBar.setVisibility(GONE);
        updateSend();
    }

    private void updateSend() {
        boolean can = input.length() > 0 || !forwarding.isEmpty() || !pendingAttachments.isEmpty();
        send.setAlpha(can ? 1f : 0.4f);
    }

    // ------------------------------------------------------------------ sending

    final List<String> pendingAttachments = new ArrayList<>();
    final List<Qvk3ImModel.Attachment> pendingPreviews = new ArrayList<>();

    /** Called by the attachment picker once an upload finished. */
    void attachUploaded(String ref, Qvk3ImModel.Attachment preview) {
        pendingAttachments.add(ref);
        pendingPreviews.add(preview);
        showContext("Вложения: " + pendingAttachments.size(), Qvk3ImModel.label(preview.type) + (pendingAttachments.size() > 1 ? " и ещё" : ""));
        updateSend();
    }

    void send() {
        String text = input.getText().toString().trim();
        if (editing != null) {
            Qvk3ImModel.Message m = editing;
            List<String> keep = new ArrayList<>();
            for (Qvk3ImModel.Attachment x : m.attachments) if (!x.ref().isEmpty()) keep.add(x.ref());
            if (text.isEmpty() && keep.isEmpty()) return;
            String old = m.text;
            m.text = text;
            m.updateTime = System.currentTimeMillis() / 1000;
            adapter.notifyDataSetChanged();
            input.setText("");
            clearContext();
            Qvk3Jobs.io(() -> {
                try {
                    Qvk3ImApi.edit(peer, m.id, text, keep);
                } catch (Exception e) {
                    Qvk3Jobs.main(() -> {
                        m.text = old;
                        adapter.notifyDataSetChanged();
                        Qvk3Ui.toast(a, "Не удалось изменить: " + Qvk3Jobs.message(e));
                    });
                }
            });
            return;
        }
        if (text.isEmpty() && forwarding.isEmpty() && pendingAttachments.isEmpty()) return;
        Qvk3ImModel.Message local = new Qvk3ImModel.Message();
        local.peer = peer;
        local.out = true;
        local.local = 1;
        local.text = text;
        local.date = System.currentTimeMillis() / 1000;
        local.randomId = (System.nanoTime() & 0x3fffffffL) + 1;
        local.attachments.addAll(pendingPreviews);
        for (Qvk3ImModel.Message m : store.messages(peer)) if (m.id == replyTo && replyTo != 0) local.reply = m;
        long reply = replyTo;
        List<Long> fwd = new ArrayList<>(forwarding);
        List<String> atts = new ArrayList<>(pendingAttachments);
        store.history.put(peer, Qvk3ImModel.merge(store.messages(peer), Collections.emptyList()));
        store.messages(peer).add(local);
        input.setText("");
        Qvk3ImDrafts.save(a, peer, "");
        pendingAttachments.clear();
        pendingPreviews.clear();
        clearContext();
        adapter.notifyDataSetChanged();
        list.post(() -> list.setSelection(adapter.getCount() - 1));
        deliver(local, reply, fwd, atts);
    }

    /** A sticker goes out at once (like in VK), as a local copy until the server confirms it. */
    void sendSticker(long id, String url) {
        Qvk3ImModel.Message local = new Qvk3ImModel.Message();
        local.peer = peer;
        local.out = true;
        local.local = 1;
        local.date = System.currentTimeMillis() / 1000;
        local.randomId = (System.nanoTime() & 0x3fffffffL) + 1;
        try {
            local.attachments.add(Qvk3ImModel.attachment(new JSONObject().put("type", "sticker").put("sticker",
                    new JSONObject().put("sticker_id", id).put("images", new JSONArray().put(new JSONObject().put("width", 256).put("url", url))))));
        } catch (Exception ignored) {
        }
        long reply = replyTo;
        store.messages(peer).add(local);
        clearContext();
        adapter.notifyDataSetChanged();
        list.post(() -> list.setSelection(adapter.getCount() - 1));
        Qvk3Jobs.io(() -> {
            try {
                long mid = Qvk3ImApi.sendSticker(peer, local.randomId, id, reply);
                JSONObject r = Qvk3ImApi.byIds(Collections.singletonList(mid));
                List<Qvk3ImModel.Message> got = Qvk3ImModel.history(r);
                Qvk3Jobs.main(() -> {
                    if (got.isEmpty()) {
                        local.id = mid;
                        local.local = 0;
                        store.notifyChanged(peer);
                    } else {
                        got.get(0).randomId = local.randomId;
                        store.put(got, false);
                    }
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    store.messages(peer).remove(local);
                    adapter.notifyDataSetChanged();
                    Qvk3Ui.toast(a, "Стикер не отправлен: " + Qvk3Jobs.message(e));
                });
            }
        });
    }

    private void deliver(Qvk3ImModel.Message local, long reply, List<Long> fwd, List<String> atts) {
        Qvk3Jobs.io(() -> {
            try {
                long id = Qvk3ImApi.send(peer, local.randomId, local.text, reply, fwd, atts);
                JSONObject r = Qvk3ImApi.byIds(Collections.singletonList(id));
                List<Qvk3ImModel.Message> got = Qvk3ImModel.history(r);
                Qvk3Jobs.main(() -> {
                    store.people.putAll(Qvk3ImModel.people(r));
                    if (got.isEmpty()) {
                        local.id = id;
                        local.local = 0;
                        store.notifyChanged(peer);
                    } else {
                        got.get(0).randomId = local.randomId;
                        store.put(got, false);
                    }
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    local.local = 2;
                    local.forwardsPending = fwd;
                    local.attachmentsPending = atts;
                    local.replyPending = reply;
                    adapter.notifyDataSetChanged();
                    Qvk3Ui.toast(a, "Сообщение не отправлено: " + Qvk3Jobs.message(e));
                });
            }
        });
    }

    private void retry(Qvk3ImModel.Message m) {
        m.local = 1;
        adapter.notifyDataSetChanged();
        deliver(m, m.replyPending, m.forwardsPending == null ? new ArrayList<>() : m.forwardsPending,
                m.attachmentsPending == null ? new ArrayList<>() : m.attachmentsPending);
    }

    // ------------------------------------------------------------------ chat menu

    private void menu() {
        Qvk3ImModel.Conversation c = store.conversation(peer);
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (c != null) {
            items.add(c.muted ? "Включить уведомления" : "Отключить уведомления");
            acts.add(() -> simple(() -> Qvk3ImApi.mute(peer, !c.muted), null, () -> {
                c.muted = !c.muted;
                store.notifyChanged(0);
            }));
            if (c.pinnedMessage != null) {
                items.add("Открепить сообщение");
                acts.add(() -> simple(() -> Qvk3ImApi.unpin(peer), null, () -> {
                    c.pinnedMessage = null;
                    changed(peer);
                }));
            }
        }
        items.add("Информация о чате\nУчастники, вложения, ссылка-приглашение");
        acts.add(() -> Qvk3ImInfo.show(a, peer, this::searchInChat));
        items.add("Вложения чата\nФото, видео, файлы, ссылки, голосовые");
        acts.add(() -> Qvk3ImInfo.Attachments.open(a, peer, "photo"));
        items.add("Звонок\nЧерез ВКонтакте");
        acts.add(() -> Qvk3ImEntry.openStock(a, peer));
        items.add("Открыть в ВК\nШтатный экран этого чата");
        acts.add(() -> Qvk3ImEntry.openStock(a, peer));
        Qvk3Ui.dialog(a).setTitle(c == null ? "Чат" : c.title).setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    private void searchInChat() {
        EditText q = new EditText(a);
        q.setHint("Текст сообщения");
        q.setSingleLine(true);
        Qvk3Ui.dialog(a).setTitle("Поиск в чате").setView(q).setPositiveButton("Найти", (d, w) -> {
            String s = q.getText().toString().trim();
            if (s.isEmpty()) return;
            Qvk3Jobs.io(() -> {
                try {
                    JSONObject r = Qvk3ImApi.search(s, peer, 50);
                    List<Qvk3ImModel.Message> found = Qvk3ImModel.history(r);
                    Collections.reverse(found);
                    Qvk3Jobs.main(() -> {
                        store.people.putAll(Qvk3ImModel.people(r));
                        if (found.isEmpty()) {
                            Qvk3Ui.toast(a, "Ничего не найдено");
                            return;
                        }
                        String[] labels = new String[found.size()];
                        for (int i = 0; i < labels.length; i++) {
                            Qvk3ImModel.Message m = found.get(i);
                            Qvk3ImModel.Person p = store.people.get(m.from);
                            labels[i] = Qvk3ImModel.preview(m, store.people) + "\n" + (p == null ? "" : p.name + " · ") + Qvk3ImUi.listTime(m.date);
                        }
                        Qvk3Ui.dialog(a).setTitle("Найдено: " + found.size()).setItems(labels, (d2, i) -> jumpTo(found.get(i))).show();
                    });
                } catch (Exception e) {
                    Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Поиск не удался: " + Qvk3Jobs.message(e)));
                }
            });
        }).setNegativeButton("Отмена", null).show();
    }

    // ------------------------------------------------------------------ list

    static final class Row {
        final int kind; // 0 date, 1 message, 2 unread divider
        final Qvk3ImModel.Message msg;
        final long date;
        boolean first, last;

        Row(int kind, Qvk3ImModel.Message msg, long date) {
            this.kind = kind;
            this.msg = msg;
            this.date = date;
        }
    }

    final class Adapter extends BaseAdapter {
        List<Row> rows = new ArrayList<>();

        @Override
        public void notifyDataSetChanged() {
            List<Row> r = new ArrayList<>();
            List<Qvk3ImModel.Message> ms = store.messages(peer);
            Qvk3ImModel.Conversation c = store.conversation(peer);
            long inRead = c == null ? Long.MAX_VALUE : c.inRead;
            boolean unreadMarked = false;
            for (int i = 0; i < ms.size(); i++) {
                Qvk3ImModel.Message m = ms.get(i);
                Qvk3ImModel.Message prev = i > 0 ? ms.get(i - 1) : null, next = i + 1 < ms.size() ? ms.get(i + 1) : null;
                if (prev == null || !Qvk3ImUi.sameDay(prev.date, m.date)) r.add(new Row(0, null, m.date));
                if (!unreadMarked && !m.out && m.cmid > inRead && c != null && c.unread > 0) {
                    r.add(new Row(2, null, m.date));
                    unreadMarked = true;
                }
                Row row = new Row(1, m, m.date);
                row.first = prev == null || prev.from != m.from || prev.service() || m.date - prev.date > 300 || !Qvk3ImUi.sameDay(prev.date, m.date);
                row.last = next == null || next.from != m.from || next.service() || next.date - m.date > 300 || !Qvk3ImUi.sameDay(next.date, m.date);
                r.add(row);
            }
            rows = r;
            super.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            Row r = rows.get(i);
            return r.msg != null ? r.msg.id : -i;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = rows.get(i);
            Qvk3Ui.Palette p = look.p;
            if (r.kind == 0 || r.kind == 2) {
                TextView t = Qvk3Ui.text(a, r.kind == 0 ? Qvk3ImUi.day(r.date) : "Непрочитанные сообщения", 13, r.kind == 2 ? p.accent : p.text2, true);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(12), dp(4), dp(12), dp(5));
                if (r.kind == 0) t.setBackground(Qvk3Ui.rounded(p.dark ? 0x33000000 : 0x14000000, dp(12)));
                FrameLayout f = new FrameLayout(a);
                f.setPadding(0, dp(10), 0, dp(6));
                f.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
                return f;
            }
            Qvk3ImModel.Message m = r.msg;
            Qvk3ImModel.Conversation c = store.conversation(peer);
            boolean chat = c != null && c.chat();
            if (m.service()) {
                TextView t = Qvk3Ui.text(a, Qvk3ImModel.serviceText(m, store.people), 13, p.text2, false);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(32), dp(6), dp(32), dp(6));
                return t;
            }
            LinearLayout row = new LinearLayout(a);
            row.setGravity(m.out ? Gravity.END | Gravity.BOTTOM : Gravity.START | Gravity.BOTTOM);
            row.setPadding(dp(8), r.first ? dp(5) : dp(1), dp(8), r.last ? dp(3) : dp(1));
            if (selected.contains(m.id)) row.setBackgroundColor((look.accent & 0x00FFFFFF) | 0x26000000);
            else if (flash == m.id && m.id != 0) row.setBackgroundColor((look.accent & 0x00FFFFFF) | 0x1A000000);
            if (chat && !m.out) {
                ImageView av = new ImageView(a);
                if (r.last) {
                    Qvk3ImModel.Person who = store.people.get(m.from);
                    Qvk3ImUi.avatar(av, who == null ? "" : who.photo, who == null ? "" : who.name, 32);
                }
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(32), dp(32));
                al.rightMargin = dp(6);
                row.addView(av, al);
            }
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(VERTICAL);
            col.setGravity(m.out ? Gravity.END : Gravity.START);
            View b = bubbles.bubble(m, chat, r.first, r.last);
            col.addView(b, new LinearLayout.LayoutParams(-2, -2));
            View reactions = bubbles.reactions(m);
            if (reactions != null) {
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
                rl.topMargin = dp(3);
                col.addView(reactions, rl);
            }
            row.addView(col);
            b.setOnClickListener(v -> tapMessage(m));
            b.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                longPress(m);
                return true;
            });
            Qvk3ImSwipe.attach(row, b, m.out, () -> {
                if (m.local == 0 && !m.service() && (c == null || c.canWrite)) reply(m);
            });
            return row;
        }
    }
}
