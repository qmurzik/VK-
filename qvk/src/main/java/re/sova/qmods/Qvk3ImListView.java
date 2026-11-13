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
                String q = e.toString().trim();
                if (msgSearch != null) {
                    msgSearch.setVisibility(q.length() >= 2 ? VISIBLE : GONE);
                    msgSearch.setText("Искать «" + q + "» в сообщениях всех чатов");
                }
            }
        });
        LinearLayout.LayoutParams sl = Qvk3Ui.match();
        sl.setMargins(dp(16), dp(4), dp(16), dp(8));
        addView(search, sl);

        // folders: Все · Непрочитанные · Беседы · Важные · Архив
        android.widget.HorizontalScrollView fs = new android.widget.HorizontalScrollView(a);
        fs.setHorizontalScrollBarEnabled(false);
        fs.setClipToPadding(false);
        fs.setPadding(dp(12), 0, dp(12), dp(6));
        folderRow = new LinearLayout(a);
        fs.addView(folderRow);
        addView(fs, Qvk3Ui.match());
        buildFolders();

        // search in the text of messages of every chat (the field above filters chat names)
        msgSearch = Qvk3Ui.line(a, "", 15, p.accent, true);
        msgSearch.setPadding(dp(20), dp(10), dp(20), dp(10));
        msgSearch.setBackground(Qvk3Ui.ripple(a, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
        msgSearch.setVisibility(GONE);
        msgSearch.setOnClickListener(v -> searchMessages(search.getText().toString().trim()));
        addView(msgSearch, Qvk3Ui.match());

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
        if (folderRow != null) buildFolders();
        updateEmpty();
    }

    /** Back: clears the search first. */
    boolean back() {
        if (search.length() > 0) {
            search.setText("");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ folders

    private static final String[][] FOLDERS = {{"all", "Все"}, {"unread", "Непрочитанные"}, {"chats", "Беседы"},
            {"important", "Важные"}, {"archive", "Архив"}};
    private String folder = "all";
    private LinearLayout folderRow;

    private void buildFolders() {
        folderRow.removeAllViews();
        Qvk3Ui.Palette p = look.p;
        int unread = 0;
        for (Qvk3ImModel.Conversation c : store.conversations) if (c.unread > 0 && !c.archived && !c.muted) unread++;
        for (String[] f : FOLDERS) {
            boolean on = f[0].equals(folder);
            String label = f[1] + ("unread".equals(f[0]) && unread > 0 ? "  " + unread : "");
            TextView chip = Qvk3Ui.line(a, label, 14, on ? p.onAccent : p.text, on);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(100));
            bg.setColor(on ? p.accent : p.surface2);
            chip.setBackground(bg);
            chip.setOnClickListener(v -> {
                folder = f[0];
                if ("archive".equals(folder) && !store.archiveLoaded) store.loadArchive();
                buildFolders();
                adapter.notifyDataSetChanged();
                if (adapter.getCount() > 0) list.setSelection(0);   // BUG-0075: an empty folder has no row 0
                updateEmpty();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            folderRow.addView(chip, lp);
        }
    }

    private boolean inFolder(Qvk3ImModel.Conversation c) {
        switch (folder) {
            case "unread":
                return !c.archived && c.unread > 0;
            case "chats":
                return !c.archived && c.chat();
            case "important":
                return c.important || c.pinned;
            case "archive":
                return c.archived;
            default:
                return !c.archived;
        }
    }

    private void updateEmpty() {
        boolean none = adapter.getCount() == 0;
        empty.setVisibility(none ? VISIBLE : GONE);
        if (!none) return;
        if (!"all".equals(folder)) {
            empty.setText("archive".equals(folder) && store.archiveLoading ? "Загружаем архив…" : "В этой папке пока пусто");
            return;
        }
        empty.setText(store.listLoading || !store.listLoaded ? "Загружаем чаты…"
                : !store.listError.isEmpty() ? "Не удалось загрузить чаты: " + store.listError + "\n\nНажмите, чтобы повторить" : "Чатов пока нет");
    }

    private TextView msgSearch;

    private void searchMessages(String q) {
        if (q.length() < 2) return;
        Qvk3ImUi.hideKeyboard(search);
        Qvk3Ui.toast(a, "Ищем «" + q + "»…");
        Qvk3Jobs.io(() -> {
            try {
                org.json.JSONObject r = Qvk3ImApi.search(q, 0, 100);
                List<Qvk3ImModel.Message> found = Qvk3ImModel.history(r);
                java.util.Map<Long, Qvk3ImModel.Person> people = Qvk3ImModel.people(r);
                Qvk3Jobs.main(() -> {
                    store.people.putAll(people);
                    if (found.isEmpty()) {
                        Qvk3Ui.toast(a, "Ничего не найдено");
                        return;
                    }
                    String[] labels = new String[found.size()];
                    for (int i = 0; i < found.size(); i++) {
                        Qvk3ImModel.Message m = found.get(i);
                        Qvk3ImModel.Conversation c = store.conversation(m.peer);
                        Qvk3ImModel.Person who = store.people.get(m.from);
                        String chat = c != null ? c.title : who != null ? who.name : "Чат";
                        labels[i] = chat + " · " + Qvk3ImUi.listTime(m.date) + "\n"
                                + (m.out ? "Вы: " : (c != null && c.chat() && who != null ? who.first + ": " : "")) + Qvk3ImModel.preview(m, store.people);
                    }
                    Qvk3Ui.dialog(a).setTitle("«" + q + "» · " + found.size()).setItems(labels, (d, w) -> Qvk3ImChatActivity.open(a, found.get(w).peer))
                            .setNegativeButton("Закрыть", null).show();
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Поиск не удался: " + Qvk3Jobs.message(e)));
            }
        });
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
                    List<String> rows = new ArrayList<>();
                    rows.add("➕ Создать беседу\nВыберите участников и название");
                    rows.addAll(labels);
                    Qvk3Ui.dialog(a).setTitle("Новое сообщение").setItems(rows.toArray(new String[0]), (d, w) -> {
                        if (w == 0) createChat(ids, labels);
                        else Qvk3ImChatActivity.open(a, ids.get(w - 1));
                    }).setNegativeButton("Отмена", null).show();
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось загрузить друзей: " + Qvk3Jobs.message(e)));
            }
        });
    }

    /** New group chat: pick friends, give it a name, messages.createChat, open it. */
    private void createChat(List<Long> ids, List<String> labels) {
        String[] names = new String[labels.size()];
        for (int i = 0; i < names.length; i++) names[i] = labels.get(i).split("\n")[0];
        boolean[] on = new boolean[names.length];
        Qvk3Ui.dialog(a).setTitle("Участники беседы").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Дальше", (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    StringBuilder who = new StringBuilder();
                    for (int i = 0; i < on.length; i++) if (on[i]) {
                        chosen.add(Long.toString(ids.get(i)));
                        if (who.length() < 40) who.append(who.length() == 0 ? "" : ", ").append(names[i].split(" ")[0]);
                    }
                    if (chosen.isEmpty()) {
                        Qvk3Ui.toast(a, "Выберите хотя бы одного участника");
                        return;
                    }
                    EditText t = new EditText(a);
                    t.setSingleLine(true);
                    t.setHint(who.toString());
                    FrameLayout box = new FrameLayout(a);
                    box.setPadding(dp(20), dp(8), dp(20), 0);
                    box.addView(t);
                    Qvk3Ui.dialog(a).setTitle("Название беседы").setView(box).setPositiveButton("Создать", (d2, w2) -> {
                        String title = t.getText().toString().trim().isEmpty() ? who.toString() : t.getText().toString().trim();
                        Qvk3Jobs.io(() -> {
                            try {
                                org.json.JSONObject r = Qvk3ImApi.call("messages.createChat", "user_ids", String.join(",", chosen), "title", title);
                                long chatId = r.optLong("chat_id", Qvk3ImApi.number(r));
                                if (chatId <= 0) throw new java.io.IOException("ВК не вернул номер беседы");
                                Qvk3Jobs.main(() -> {
                                    store.loadConversations(false);
                                    Qvk3ImChatActivity.open(a, 2_000_000_000L + chatId);
                                });
                            } catch (Exception e) {
                                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Беседа не создана: " + Qvk3Jobs.message(e)));
                            }
                        });
                    }).setNegativeButton("Отмена", null).show();
                }).setNegativeButton("Отмена", null).show();
    }

    /** Hidden by QVK's «Скрыть диалог» and not revealed right now. */
    static boolean hidden(long peer) {
        try {
            return HiddenDialogs.contains(peer) && !HiddenDialogs.revealed();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hiddenSaved(long peer) {
        try {
            return HiddenDialogs.contains(peer);
        } catch (Throwable t) {
            return false;
        }
    }

    private void menu() {
        int hiddenCount;
        boolean revealed;
        try {
            hiddenCount = HiddenDialogs.count();
            revealed = HiddenDialogs.revealed();
        } catch (Throwable t) {
            hiddenCount = 0;
            revealed = false;
        }
        String hiddenItem = hiddenCount == 0 ? "Скрытые чаты\nСкрыть чат: удержание чата → «Скрыть диалог»"
                : revealed ? "Спрятать скрытые чаты" : "Показать скрытые чаты (" + hiddenCount + ")";
        Qvk3Ui.dialog(a).setTitle("Сообщения").setItems(new String[]{
                "Отметить все прочитанными\nВо всех чатах с непрочитанными",
                "Обновить\nЗагрузить список заново",
                "Открыть мессенджер ВК\nШтатный экран ВКонтакте",
                "Настройки мессенджера QVK",
                hiddenItem}, (d, w) -> {
            if (w == 4) {
                try {
                    HiddenDialogs.toggle();
                } catch (Throwable t) {
                    RuntimeFaults.record("qvk3.im.reveal", t);
                }
                adapter.notifyDataSetChanged();
                return;
            }
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
        acts.add(() -> act(() -> Qvk3ImApi.archive(c.peer, !c.archived), () -> c.archived = !c.archived));
        boolean isHidden = hiddenSaved(c.peer);
        items.add(isHidden ? "Показать диалог\nВернуть в общий список" : "Скрыть диалог\nСкрытые видны по «Показать скрытые» в меню");
        acts.add(() -> {
            try {
                HiddenDialogs.toggle(c.peer);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.im.hide", t);
            }
            adapter.notifyDataSetChanged();
            Qvk3Ui.toast(a, isHidden ? "Диалог снова в списке" : "Диалог скрыт");
        });
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
                if (hidden(c.peer)) continue; // BUG-0036: QVK's hidden chats stay hidden here too
                if (query.isEmpty() && !inFolder(c)) continue;
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
            return i >= 0 && i < shown.size() ? shown.get(i) : null;
        }

        @Override
        public long getItemId(int i) {
            return i >= 0 && i < shown.size() ? shown.get(i).peer : -1;
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
