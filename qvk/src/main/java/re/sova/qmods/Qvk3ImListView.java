package re.sova.qmods;

import android.app.Activity;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation list of the QVK messenger. Hosted inside VK's own «Мессенджер» tab (its fragment returns this view),
 * so VK's bottom navigation, back stack and notifications stay as they are.
 */
final class Qvk3ImListView extends LinearLayout implements Qvk3ImStore.Listener {
    private final Activity a;
    private final Qvk3ImStore store;
    private final Qvk3ImUi.Look look;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final TextView empty;
    private final EditText search;
    private String query = "";

    Qvk3ImListView(Activity a) {
        super(a);
        this.a = a;
        this.store = Qvk3ImStore.get(a);
        this.look = new Qvk3ImUi.Look(a);
        Qvk3Ui.Palette p = look.p;
        setOrientation(VERTICAL);
        setBackgroundColor(p.bg);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(4));
        TextView title = Qvk3Ui.line(a, "Сообщения", 22, p.text, true);
        title.setAccessibilityHeading(true);
        head.addView(title, Qvk3Ui.weight(1));
        head.addView(Qvk3Ui.icon(a, Qvk3Icons.PLUS, p.text, 44, 22, "Новое сообщение", v -> compose()));
        head.addView(Qvk3Ui.icon(a, Qvk3Icons.MORE, p.text, 44, 22, "Ещё", v -> menu()));
        addView(head, Qvk3Ui.match());

        search = Qvk3Sheet.field(a, p, new EditText(a));
        search.setHint("Поиск по чатам");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(dp(14), dp(9), dp(14), dp(9));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                query = Qvk3Lyrics.normal(e.toString());
                adapter.notifyDataSetChanged();
            }
        });
        LinearLayout.LayoutParams sl = Qvk3Ui.match();
        sl.setMargins(dp(16), dp(4), dp(16), dp(8));
        addView(search, sl);

        FrameLayout box = new FrameLayout(a);
        list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> open(adapter.item(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            actions(adapter.item(pos));
            return true;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {
                if (s != SCROLL_STATE_IDLE) Qvk3ImUi.hideKeyboard(search);
            }

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 6 && query.isEmpty()) store.loadConversations(true);
            }
        });
        box.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = Qvk3Ui.text(a, "", 15, p.text2, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), 0, dp(32), 0);
        empty.setOnClickListener(v -> store.loadConversations(false));
        box.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        addView(box, new LinearLayout.LayoutParams(-1, 0, 1));
        changed(0);
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        store.addListener(this);
        store.loadConversations(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        store.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void changed(long peer) {
        adapter.notifyDataSetChanged();
        boolean none = store.conversations.isEmpty();
        empty.setVisibility(none ? VISIBLE : GONE);
        if (none) empty.setText(store.listLoading || !store.listLoaded ? "Загружаем чаты…"
                : !store.listError.isEmpty() ? "Не удалось загрузить чаты: " + store.listError + "\n\nНажмите, чтобы повторить" : "Чатов пока нет");
    }

    /** Back: clears the search first. */
    boolean back() {
        if (search.length() > 0) {
            search.setText("");
            return true;
        }
        return false;
    }

    void open(Qvk3ImModel.Conversation c) {
        if (c == null) return;
        Qvk3ImUi.hideKeyboard(search);
        Qvk3ImChatActivity.open(a, c.peer);
    }

    /** «Новое сообщение»: friends in VK's «important first» order. */
    private void compose() {
        Qvk3Ui.toast(a, "Загружаем друзей…");
        Qvk3Jobs.io(() -> {
            try {
                org.json.JSONObject r = Qvk3ImApi.call("friends.get", "order", "hints", "count", "300", "fields", "photo_100,online");
                org.json.JSONArray items = r.optJSONArray("items");
                List<Long> ids = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject o = items.optJSONObject(i);
                    if (o == null || o.has("deactivated")) continue;
                    ids.add(o.optLong("id"));
                    labels.add((o.optString("first_name") + " " + o.optString("last_name")).trim() + "\n" + (o.optInt("online") == 1 ? "в сети" : "друг"));
                }
                Qvk3Jobs.main(() -> {
                    if (ids.isEmpty()) {
                        Qvk3Ui.toast(a, "Список друзей пуст");
                        return;
                    }
                    Qvk3Ui.dialog(a).setTitle("Новое сообщение").setItems(labels.toArray(new String[0]), (d, w) -> Qvk3ImChatActivity.open(a, ids.get(w)))
                            .setNegativeButton("Отмена", null).show();
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось загрузить друзей: " + Qvk3Jobs.message(e)));
            }
        });
    }

    private void menu() {
        Qvk3Ui.dialog(a).setTitle("Сообщения").setItems(new String[]{
                "Отметить все прочитанными\nВо всех чатах с непрочитанными",
                "Обновить\nЗагрузить список заново",
                "Открыть мессенджер ВК\nШтатный экран ВКонтакте",
                "Настройки мессенджера QVK"}, (d, w) -> {
            switch (w) {
                case 0:
                    for (Qvk3ImModel.Conversation c : new ArrayList<>(store.conversations)) if (c.unread > 0) {
                        Qvk3Jobs.io(() -> {
                            try {
                                Qvk3ImApi.markRead(c.peer);
                            } catch (Exception ignored) {
                            }
                        });
                        store.read(c.peer, c.last == null ? 0 : c.last.cmid, false);
                    }
                    break;
                case 1:
                    store.loadConversations(false);
                    break;
                case 2:
                    Qvk3ImEntry.openStock(a, 0);
                    break;
                default:
                    Qvk3ImEntry.settings(a);
            }
        }).show();
    }

    private void actions(Qvk3ImModel.Conversation c) {
        if (c == null) return;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        items.add(c.pinned ? "Открепить" : "Закрепить");
        acts.add(() -> act(() -> Qvk3ImApi.pinConversation(c.peer, !c.pinned), () -> {
            c.pinned = !c.pinned;
            store.sort();
        }));
        items.add(c.muted ? "Включить уведомления" : "Отключить уведомления");
        acts.add(() -> act(() -> Qvk3ImApi.mute(c.peer, !c.muted), () -> c.muted = !c.muted));
        items.add(c.unread > 0 ? "Отметить прочитанным" : "Отметить непрочитанным");
        acts.add(() -> act(() -> {
            if (c.unread > 0) Qvk3ImApi.markRead(c.peer);
            else Qvk3ImApi.markUnread(c.peer);
        }, () -> c.unread = c.unread > 0 ? 0 : 1));
        items.add(c.archived ? "Вернуть из архива" : "Архивировать");
        acts.add(() -> act(() -> Qvk3ImApi.archive(c.peer, !c.archived), () -> {
            store.conversations.remove(c);
            store.byPeer.remove(c.peer);
        }));
        items.add("Открыть в ВК\nШтатный экран чата");
        acts.add(() -> Qvk3ImEntry.openStock(a, c.peer));
        items.add("Удалить чат\nИстория удалится только у вас");
        acts.add(() -> Qvk3Ui.dialog(a).setTitle("Удалить чат «" + c.title + "»?")
                .setMessage("История сообщений удалится у вас. Отменить это нельзя.")
                .setPositiveButton("Удалить", (d, w) -> act(() -> Qvk3ImApi.deleteConversation(c.peer), () -> {
                    store.conversations.remove(c);
                    store.byPeer.remove(c.peer);
                })).setNegativeButton("Отмена", null).show());
        Qvk3Ui.dialog(a).setTitle(c.title).setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    interface Call {
        void run() throws Exception;
    }

    /** Server call off the main thread, then a local update and a redraw; a toast on failure. */
    private void act(Call call, Runnable local) {
        Qvk3Jobs.io(() -> {
            try {
                call.run();
                Qvk3Jobs.main(() -> {
                    local.run();
                    store.notifyChanged(0);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось: " + Qvk3Jobs.message(e)));
            }
        });
    }

    // ------------------------------------------------------------------ rows

    final class Adapter extends BaseAdapter {
        List<Qvk3ImModel.Conversation> shown = new ArrayList<>();

        @Override
        public void notifyDataSetChanged() {
            shown = new ArrayList<>();
            for (Qvk3ImModel.Conversation c : store.conversations) {
                if (!query.isEmpty() && !Qvk3Lyrics.normal(c.title).contains(query)) continue;
                shown.add(c);
            }
            super.notifyDataSetChanged();
        }

        Qvk3ImModel.Conversation item(int i) {
            return i >= 0 && i < shown.size() ? shown.get(i) : null;
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return shown.get(i).peer;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = convert != null && convert.getTag() instanceof Row ? (Row) convert.getTag() : new Row();
            r.bind(shown.get(i));
            return r.root;
        }
    }

    final class Row {
        final LinearLayout root = new LinearLayout(a);
        final ImageView avatar = new ImageView(a);
        final View onlineDot = new View(a);
        final TextView title, time, preview, badge;
        final ImageView pin = new ImageView(a), tick = new ImageView(a);

        Row() {
            Qvk3Ui.Palette p = look.p;
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(12), dp(8), dp(14), dp(8));
            root.setMinimumHeight(dp(72));
            root.setTag(this);
            FrameLayout av = new FrameLayout(a);
            av.addView(avatar, new FrameLayout.LayoutParams(dp(56), dp(56)));
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(0xFF4BB34B);
            dot.setStroke(dp(2.5f), p.bg);
            onlineDot.setBackground(dot);
            av.addView(onlineDot, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.BOTTOM | Gravity.END));
            root.addView(av, new LinearLayout.LayoutParams(dp(56), dp(56)));
            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(VERTICAL);
            mid.setPadding(dp(12), 0, 0, 0);
            LinearLayout top = new LinearLayout(a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            title = Qvk3Ui.line(a, "", 16, p.text, true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(tick, new LinearLayout.LayoutParams(dp(16), dp(16)));
            time = Qvk3Ui.text(a, "", 13, p.text2, false);
            time.setPadding(dp(4), 0, 0, 0);
            top.addView(time);
            mid.addView(top);
            LinearLayout bottom = new LinearLayout(a);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            bottom.setPadding(0, dp(3), 0, 0);
            preview = Qvk3Ui.text(a, "", 14, p.text2, false);
            preview.setMaxLines(2);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            bottom.addView(preview, new LinearLayout.LayoutParams(0, -2, 1f));
            bottom.addView(pin, new LinearLayout.LayoutParams(dp(16), dp(16)));
            badge = Qvk3Ui.text(a, "", 13, 0xFFFFFFFF, true);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(22));
            badge.setPadding(dp(7), dp(1), dp(7), dp(2));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
            bl.leftMargin = dp(6);
            bottom.addView(badge, bl);
            mid.addView(bottom);
            root.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            root.setBackground(Qvk3Ui.ripple(a, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
        }

        void bind(Qvk3ImModel.Conversation c) {
            Qvk3Ui.Palette p = look.p;
            Qvk3ImUi.avatar(avatar, c.photo, c.title, 56);
            onlineDot.setVisibility(c.online && !c.chat() ? VISIBLE : GONE);
            title.setText(c.title);
            String typing = store.typingText(c.peer, c.chat());
            if (!typing.isEmpty()) {
                preview.setText(typing);
                preview.setTextColor(p.accent);
            } else {
                String text = Qvk3ImModel.preview(c.last, store.people);
                if (c.last != null && !c.last.service()) {
                    if (c.last.out) text = "Вы: " + text;
                    else if (c.chat()) {
                        Qvk3ImModel.Person who = store.people.get(c.last.from);
                        if (who != null) text = who.first + ": " + text;
                    }
                }
                preview.setText(text);
                preview.setTextColor(p.text2);
            }
            time.setText(Qvk3ImUi.listTime(c.last == null ? 0 : c.last.date));
            boolean showTick = c.last != null && c.last.out && !c.last.service();
            tick.setVisibility(showTick ? VISIBLE : GONE);
            if (showTick) tick.setImageDrawable(Qvk3Icons.of(Qvk3Icons.CHECK, c.outgoingRead() ? p.accent : p.text2));
            pin.setVisibility(c.pinned && c.unread == 0 ? VISIBLE : GONE);
            pin.setImageDrawable(Qvk3Icons.of(Qvk3Icons.STAR, p.text2));
            badge.setVisibility(c.unread > 0 ? VISIBLE : GONE);
            badge.setText(c.unread > 999 ? "999+" : String.valueOf(c.unread));
            badge.setBackground(Qvk3Ui.rounded(c.muted ? (p.dark ? 0xFF5C5F66 : 0xFFAEB7C2) : p.accent, dp(11)));
            title.setTypeface(Typeface.DEFAULT, c.unread > 0 ? Typeface.BOLD : Typeface.NORMAL);
            root.setContentDescription(c.title + ". " + preview.getText() + (c.unread > 0 ? ". Непрочитанных: " + c.unread : ""));
        }
    }
}
