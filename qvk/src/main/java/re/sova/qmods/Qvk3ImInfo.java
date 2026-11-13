package re.sova.qmods;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chat info of the QVK messenger — everything that used to send people back to VK's own screens: profile header
 * with quick actions, every attachment of the chat by kind (photos and videos as a grid, files, links, voice
 * messages), and for group chats the members, the invite link, renaming and leaving. Calls still go to VK: VK's
 * calls run on its own VoIP stack, which QVK cannot drive from outside.
 */
final class Qvk3ImInfo {
    private final Activity a;
    private final long peer;
    private final Qvk3ImStore store;
    private final Qvk3ImUi.Look look;
    private final Qvk3Ui.Palette p;
    private final Dialog dialog;
    private final LinearLayout col;
    private final Runnable onSearch;
    private LinearLayout membersBox;
    private TextView membersTitle;
    private boolean iAmAdmin;

    private Qvk3ImInfo(Activity a, long peer, Runnable onSearch) {
        this.a = a;
        this.peer = peer;
        this.onSearch = onSearch;
        store = Qvk3ImStore.get(a);
        look = new Qvk3ImUi.Look(a);
        p = look.p;
        dialog = fullScreen(a, p.bg);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.addView(topBar(a, p, "Информация", dialog), new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(a);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(24));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        root.setFitsSystemWindows(true);
    }

    static void show(Activity a, long peer, Runnable onSearch) {
        Qvk3ImInfo i = new Qvk3ImInfo(a, peer, onSearch);
        i.build();
        i.dialog.show();
        slideIn(i.dialog);
    }

    // ------------------------------------------------------------------ main screen

    private void build() {
        Qvk3ImModel.Conversation c = store.conversation(peer);
        String title = c == null ? "Чат" : c.title;
        boolean chat = peer > 2_000_000_000L;

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(dp(16), dp(18), dp(16), dp(10));
        ImageView av = new ImageView(a);
        Qvk3ImUi.avatar(av, c == null ? "" : c.photo, title, 96);
        head.addView(av, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView t = Qvk3Ui.text(a, title, 22, p.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, 0);
        head.addView(t);
        String sub = c == null ? "" : chat ? (c.members > 0 ? plural(c.members, "участник", "участника", "участников") : "Беседа")
                : Qvk3ImUi.status(store, c);
        TextView st = Qvk3Ui.text(a, sub, 14, p.text2, false);
        st.setGravity(Gravity.CENTER);
        head.addView(st);
        col.addView(head);

        // quick actions
        LinearLayout acts = new LinearLayout(a);
        acts.setGravity(Gravity.CENTER);
        acts.setPadding(dp(8), dp(6), dp(8), dp(14));
        boolean muted = c != null && c.muted;
        acts.addView(action(Qvk3Icons.BELL, muted ? "Включить звук" : "Без звука", v -> {
            if (c == null) return;
            run(() -> Qvk3ImApi.mute(peer, !c.muted), () -> {
                c.muted = !c.muted;
                store.notifyChanged(0);
                rebuild();
            });
        }));
        acts.addView(action(Qvk3Icons.SEARCH, "Поиск", v -> {
            dialog.dismiss();
            if (onSearch != null) onSearch.run();
        }));
        acts.addView(action(Qvk3Icons.STAR, c != null && c.pinned ? "Открепить" : "Закрепить", v -> {
            if (c == null) return;
            run(() -> Qvk3ImApi.pinConversation(peer, !c.pinned), () -> {
                c.pinned = !c.pinned;
                store.sort();
                store.notifyChanged(0);
                rebuild();
            });
        }));
        acts.addView(action(Qvk3Icons.PULSE, "Звонок", v -> Qvk3ImEntry.openStock(a, peer)));
        col.addView(acts);

        // attachments
        section("Вложения");
        LinearLayout att = card();
        att.addView(row(Qvk3Icons.IMAGE, "Фотографии", null, v -> Attachments.open(a, peer, "photo")));
        att.addView(divider());
        att.addView(row(Qvk3Icons.PLAY, "Видео", null, v -> Attachments.open(a, peer, "video")));
        att.addView(divider());
        att.addView(row(Qvk3Icons.FOLDER, "Файлы", null, v -> Attachments.open(a, peer, "doc")));
        att.addView(divider());
        att.addView(row(Qvk3Icons.SEND, "Ссылки", null, v -> Attachments.open(a, peer, "link")));
        att.addView(divider());
        att.addView(row(Qvk3Icons.PULSE, "Голосовые сообщения", null, v -> Attachments.open(a, peer, "audio_message")));
        att.addView(divider());
        att.addView(row(Qvk3Icons.MUSIC, "Музыка", null, v -> Attachments.open(a, peer, "audio")));
        col.addView(att, cardLp());

        if (chat) {
            membersTitle = section("Участники");
            membersBox = card();
            TextView loading = Qvk3Ui.text(a, "Загружаем участников…", 14, p.text2, false);
            loading.setPadding(dp(16), dp(14), dp(16), dp(14));
            membersBox.addView(loading);
            col.addView(membersBox, cardLp());
            loadMembers();

            section("Беседа");
            LinearLayout box = card();
            box.addView(row(Qvk3Icons.SEND, "Ссылка-приглашение", "Скопировать или поделиться", v -> invite(false)));
            box.addView(divider());
            box.addView(row(Qvk3Icons.WAND, "Переименовать беседу", null, v -> rename(c)));
            box.addView(divider());
            box.addView(dangerRow("Выйти из беседы", v -> confirm("Выйти из беседы «" + title + "»?",
                    "Вы перестанете получать сообщения. Вернуться можно по ссылке-приглашению.", "Выйти",
                    () -> run(() -> Qvk3ImApi.leaveChat(peer), () -> {
                        Qvk3Ui.toast(a, "Вы вышли из беседы");
                        dialog.dismiss();
                    }))));
            col.addView(box, cardLp());
        } else {
            section("Профиль");
            LinearLayout box = card();
            String link = peer > 0 ? "https://vk.com/id" + peer : "https://vk.com/club" + (-peer);
            box.addView(row(Qvk3Icons.USER, peer > 0 ? "Открыть профиль" : "Открыть сообщество", "vk.com", v -> Qvk3ImUi.openLink(a, link)));
            col.addView(box, cardLp());
        }

        section("Чат");
        LinearLayout more = card();
        boolean archived = c != null && c.archived;
        more.addView(row(Qvk3Icons.FOLDER, archived ? "Вернуть из архива" : "Архивировать", null, v -> {
            if (c == null) return;
            run(() -> Qvk3ImApi.archive(peer, !c.archived), () -> {
                c.archived = !c.archived;
                store.notifyChanged(0);
                rebuild();
            });
        }));
        more.addView(divider());
        more.addView(row(Qvk3Icons.NAV, "Открыть в ВК", "Штатный экран этого чата", v -> Qvk3ImEntry.openStock(a, peer)));
        more.addView(divider());
        more.addView(dangerRow("Очистить историю", v -> confirm("Очистить историю?", "Сообщения удалятся только у вас. Отменить нельзя.",
                "Очистить", () -> run(() -> Qvk3ImApi.deleteConversation(peer), () -> {
                    store.history.remove(peer);
                    store.historyComplete.remove(peer);
                    store.notifyChanged(peer);
                    Qvk3Ui.toast(a, "История очищена");
                }))));
        col.addView(more, cardLp());

        // gentle entrance of the sections
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(14));
            v.animate().alpha(1f).translationY(0).setStartDelay(40L + i * 30L).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void rebuild() {
        col.removeAllViews();
        build();
    }

    // ------------------------------------------------------------------ members

    private void loadMembers() {
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.members(peer);
                Map<Long, Qvk3ImModel.Person> people = Qvk3ImModel.people(r);
                JSONArray items = r.optJSONArray("items");
                long me = Qvk3MusicSource.account();
                List<long[]> list = new ArrayList<>();   // id, role (2 owner, 1 admin, 0)
                boolean admin = false;
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.optJSONObject(i);
                    if (o == null) continue;
                    long id = o.optLong("member_id");
                    int role = o.optBoolean("is_owner") ? 2 : o.optBoolean("is_admin") ? 1 : 0;
                    if (id == me && role > 0) admin = true;
                    list.add(new long[]{id, role});
                }
                list.sort((x, y) -> Long.compare(y[1], x[1]));
                final boolean fAdmin = admin;
                Qvk3Jobs.main(() -> {
                    store.people.putAll(people);
                    iAmAdmin = fAdmin;
                    showMembers(list, people, me);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    membersBox.removeAllViews();
                    TextView t = Qvk3Ui.text(a, "Участники недоступны: " + Qvk3Jobs.message(e), 14, p.text2, false);
                    t.setPadding(dp(16), dp(14), dp(16), dp(14));
                    membersBox.addView(t);
                });
            }
        });
    }

    private void showMembers(List<long[]> list, Map<Long, Qvk3ImModel.Person> people, long me) {
        membersBox.removeAllViews();
        if (membersTitle != null) membersTitle.setText("Участники · " + list.size());
        int shown = 0;
        for (long[] m : list) {
            if (shown++ >= 200) break;
            long id = m[0];
            Qvk3ImModel.Person who = people.get(id);
            String name = who == null ? (id < 0 ? "Сообщество" : "Пользователь " + id) : who.name;
            String role = m[1] == 2 ? "создатель" : m[1] == 1 ? "администратор" : who != null && who.online ? "в сети" : "";
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(14), dp(8), dp(14), dp(8));
            r.setBackground(Qvk3Ui.ripple(a, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
            ImageView av = new ImageView(a);
            Qvk3ImUi.avatar(av, who == null ? "" : who.photo, name, 42);
            r.addView(av, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(12), 0, 0, 0);
            tx.addView(Qvk3Ui.line(a, name + (id == me ? " (вы)" : ""), 16, p.text, false));
            if (!role.isEmpty()) tx.addView(Qvk3Ui.line(a, role, 13, m[1] > 0 ? p.accent : p.text2, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            r.setOnClickListener(v -> memberMenu(id, name, me));
            membersBox.addView(r);
        }
    }

    private void memberMenu(long id, String name, long me) {
        if (id == me) return;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (id > 0) {
            items.add("Написать");
            acts.add(() -> Qvk3ImChatActivity.open(a, id));
        }
        items.add("Открыть профиль");
        acts.add(() -> Qvk3ImUi.openLink(a, id > 0 ? "https://vk.com/id" + id : "https://vk.com/club" + (-id)));
        if (iAmAdmin) {
            items.add("Исключить из беседы");
            acts.add(() -> confirm("Исключить " + name + "?", "Участник перестанет видеть новые сообщения беседы.", "Исключить",
                    () -> run(() -> Qvk3ImApi.kick(peer, id), this::rebuild)));
        }
        Qvk3Ui.dialog(a).setTitle(name).setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    private void invite(boolean reset) {
        Qvk3Jobs.io(() -> {
            try {
                String link = Qvk3ImApi.inviteLink(peer, reset);
                Qvk3Jobs.main(() -> Qvk3Ui.dialog(a).setTitle("Ссылка-приглашение").setMessage(link)
                        .setPositiveButton("Скопировать", (d, w) -> {
                            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
                            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("QVK", link));
                            Qvk3Ui.toast(a, "Ссылка скопирована");
                        })
                        .setNeutralButton("Поделиться", (d, w) -> a.startActivity(Intent.createChooser(
                                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Поделиться ссылкой")))
                        .setNegativeButton("Новая ссылка", (d, w) -> invite(true)).show());
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Ссылка недоступна: " + Qvk3Jobs.message(e)));
            }
        });
    }

    private void rename(Qvk3ImModel.Conversation c) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setText(c == null ? "" : c.title);
        e.setSelection(e.length());
        FrameLayout box = new FrameLayout(a);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e);
        Qvk3Ui.dialog(a).setTitle("Название беседы").setView(box).setPositiveButton("Сохранить", (d, w) -> {
            String t = e.getText().toString().trim();
            if (t.isEmpty()) return;
            run(() -> Qvk3ImApi.renameChat(peer, t), () -> {
                if (c != null) c.title = t;
                store.notifyChanged(peer);
                rebuild();
            });
        }).setNegativeButton("Отмена", null).show();
    }

    // ------------------------------------------------------------------ attachments screen

    /** All attachments of one kind, with tabs to switch kinds; photos/videos as a 3-column grid. */
    static final class Attachments {
        private static final String[][] KINDS = {{"photo", "Фото"}, {"video", "Видео"}, {"doc", "Файлы"}, {"link", "Ссылки"},
                {"audio_message", "Голосовые"}, {"audio", "Музыка"}};
        private final Activity a;
        private final long peer;
        private final Qvk3Ui.Palette p;
        private final Dialog dialog;
        private final FrameLayout content;
        private final LinearLayout tabs;
        private String kind;
        private String next;
        private boolean loading;
        private final List<Qvk3ImModel.Attachment> items = new ArrayList<>();
        private BaseAdapter adapter;
        private TextView empty;

        private Attachments(Activity a, long peer, String kind) {
            this.a = a;
            this.peer = peer;
            this.kind = kind;
            p = new Qvk3ImUi.Look(a).p;
            dialog = fullScreen(a, p.bg);
            LinearLayout root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(p.bg);
            root.setFitsSystemWindows(true);
            root.addView(topBar(a, p, "Вложения чата", dialog), new LinearLayout.LayoutParams(-1, -2));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            tabs = new LinearLayout(a);
            tabs.setPadding(Qvk3Ui.dp(a, 12), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 12), Qvk3Ui.dp(a, 8));
            hs.addView(tabs);
            root.addView(hs);
            content = new FrameLayout(a);
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
            dialog.setContentView(root);
        }

        static void open(Activity a, long peer, String kind) {
            Attachments s = new Attachments(a, peer, kind);
            s.select(kind);
            s.dialog.show();
            slideIn(s.dialog);
        }

        private void select(String k) {
            kind = k;
            next = null;
            items.clear();
            tabs.removeAllViews();
            for (String[] t : KINDS) {
                boolean on = t[0].equals(kind);
                TextView chip = Qvk3Ui.line(a, t[1], 14, on ? p.onAccent : p.text, on);
                chip.setPadding(Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 7), Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 7));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(Qvk3Ui.dp(a, 100));
                bg.setColor(on ? p.accent : p.surface2);
                chip.setBackground(bg);
                chip.setOnClickListener(v -> select(t[0]));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.rightMargin = Qvk3Ui.dp(a, 8);
                tabs.addView(chip, lp);
            }
            content.removeAllViews();
            boolean grid = "photo".equals(kind) || "video".equals(kind);
            if (grid) {
                GridView g = new GridView(a);
                g.setNumColumns(3);
                int gap = Qvk3Ui.dp(a, 2);
                g.setHorizontalSpacing(gap);
                g.setVerticalSpacing(gap);
                g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
                adapter = new GridAdapter();
                g.setAdapter(adapter);
                g.setOnItemClickListener((x, v, i, id) -> openItem(i));
                g.setOnScrollListener(scroller());
                content.addView(g, new FrameLayout.LayoutParams(-1, -1));
            } else {
                ListView l = new ListView(a);
                l.setDivider(null);
                adapter = new ListAdapter();
                l.setAdapter(adapter);
                l.setOnItemClickListener((x, v, i, id) -> openItem(i));
                l.setOnScrollListener(scroller());
                content.addView(l, new FrameLayout.LayoutParams(-1, -1));
            }
            empty = Qvk3Ui.text(a, "Загружаем…", 15, p.text2, false);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            more();
        }

        private android.widget.AbsListView.OnScrollListener scroller() {
            return new android.widget.AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(android.widget.AbsListView v, int s) {
                }

                @Override
                public void onScroll(android.widget.AbsListView v, int first, int visible, int total) {
                    if (total > 0 && first + visible >= total - 9) more();
                }
            };
        }

        private void more() {
            if (loading || (next != null && next.isEmpty())) return;
            loading = true;
            final String k = kind, from = next;
            Qvk3Jobs.io(() -> {
                List<Qvk3ImModel.Attachment> got = new ArrayList<>();
                String nx = "", err = null;
                try {
                    JSONObject r = Qvk3ImApi.historyAttachments(peer, k, from, 60);
                    JSONArray arr = r.optJSONArray("items");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        JSONObject at = o == null ? null : o.optJSONObject("attachment");
                        if (at == null) continue;
                        Qvk3ImModel.Attachment x = Qvk3ImModel.attachment(at);
                        if (x != null) got.add(x);
                    }
                    nx = r.optString("next_from", "");
                } catch (Exception e) {
                    err = Qvk3Jobs.message(e);
                }
                final String fNx = nx, fErr = err;
                Qvk3Jobs.main(() -> {
                    loading = false;
                    if (!k.equals(kind)) return;
                    next = fErr != null ? "" : fNx;
                    items.addAll(got);
                    adapter.notifyDataSetChanged();
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    if (items.isEmpty()) empty.setText(fErr != null ? "Не удалось загрузить: " + fErr : "Здесь пока ничего нет");
                });
            });
        }

        private void openItem(int i) {
            if (i < 0 || i >= items.size()) return;
            Qvk3ImModel.Attachment x = items.get(i);
            switch (x.type) {
                case "photo":
                    Qvk3ImViewer.show(a, new ArrayList<>(items), i);
                    break;
                case "video":
                    Qvk3ImUi.openLink(a, "https://vk.com/video" + x.ownerId + "_" + x.id);
                    break;
                case "audio_message":
                    if (!x.url.isEmpty()) Qvk3ImVoice.toggle(a, x.url, null);
                    break;
                case "audio":
                    Qvk3Ui.toast(a, x.title + (x.subtitle.isEmpty() ? "" : " — " + x.subtitle));
                    break;
                default:
                    if (!x.url.isEmpty()) Qvk3ImUi.openLink(a, x.url);
            }
        }

        final class GridAdapter extends BaseAdapter {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int i) {
                return items.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                FrameLayout f;
                ImageView iv;
                TextView badge;
                if (convert instanceof FrameLayout) {
                    f = (FrameLayout) convert;
                    iv = (ImageView) f.getChildAt(0);
                    badge = (TextView) f.getChildAt(1);
                } else {
                    f = new FrameLayout(a) {
                        @Override
                        protected void onMeasure(int w, int h) {
                            super.onMeasure(w, w);   // square cells
                        }
                    };
                    iv = new ImageView(a);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundColor(p.surface2);
                    f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                    badge = Qvk3Ui.line(a, "", 12, Color.WHITE, true);
                    badge.setPadding(Qvk3Ui.dp(a, 6), Qvk3Ui.dp(a, 2), Qvk3Ui.dp(a, 6), Qvk3Ui.dp(a, 2));
                    GradientDrawable bb = new GradientDrawable();
                    bb.setColor(0x99000000);
                    bb.setCornerRadius(Qvk3Ui.dp(a, 8));
                    badge.setBackground(bb);
                    FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
                    bl.setMargins(0, 0, Qvk3Ui.dp(a, 6), Qvk3Ui.dp(a, 6));
                    f.addView(badge, bl);
                }
                Qvk3ImModel.Attachment x = items.get(i);
                String url = x.preview.isEmpty() ? x.url : x.preview;
                iv.setImageDrawable(null);
                iv.setTag(url);
                Qvk3Images.get(a).load(url, 300, b -> {
                    if (url.equals(iv.getTag()) && b != null) iv.setImageBitmap(b);
                });
                boolean video = "video".equals(x.type);
                badge.setVisibility(video ? View.VISIBLE : View.GONE);
                if (video) badge.setText("▶ " + Qvk3ImUi.duration(x.duration));
                return f;
            }
        }

        final class ListAdapter extends BaseAdapter {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int i) {
                return items.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                Qvk3ImModel.Attachment x = items.get(i);
                LinearLayout r = new LinearLayout(a);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 10), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 10));
                ImageView ic = new ImageView(a);
                String icon = "doc".equals(x.type) ? Qvk3Icons.FOLDER : "link".equals(x.type) ? Qvk3Icons.SEND
                        : "audio".equals(x.type) ? Qvk3Icons.MUSIC : Qvk3Icons.PULSE;
                ic.setImageDrawable(Qvk3Icons.of(icon, p.accent));
                int pad = Qvk3Ui.dp(a, 10);
                ic.setPadding(pad, pad, pad, pad);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(Qvk3Ui.dp(a, 12));
                bg.setColor(p.surface2);
                ic.setBackground(bg);
                r.addView(ic, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 44), Qvk3Ui.dp(a, 44)));
                LinearLayout tx = new LinearLayout(a);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(Qvk3Ui.dp(a, 12), 0, 0, 0);
                String title = x.title.isEmpty() ? ("audio_message".equals(x.type) ? "Голосовое сообщение" : x.url) : x.title;
                String sub = "doc".equals(x.type) ? (x.ext.toUpperCase() + (x.size > 0 ? " · " + Qvk3ImUi.size(x.size) : ""))
                        : "audio_message".equals(x.type) ? Qvk3ImUi.duration(x.duration) + (x.transcript.isEmpty() ? "" : " · " + x.transcript)
                        : x.subtitle.isEmpty() ? x.url : x.subtitle;
                tx.addView(Qvk3Ui.line(a, title, 15, p.text, false));
                tx.addView(Qvk3Ui.line(a, sub, 13, p.text2, false));
                r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
                return r;
            }
        }
    }

    // ------------------------------------------------------------------ building blocks

    static Dialog fullScreen(Activity a, int bg) {
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(bg));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setWindowAnimations(0);
        }
        return d;
    }

    static void slideIn(Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        View v = w.getDecorView();
        v.setTranslationX(v.getResources().getDisplayMetrics().widthPixels * 0.25f);
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    static LinearLayout topBar(Activity a, Qvk3Ui.Palette p, String title, Dialog d) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int d4 = Qvk3Ui.dp(a, 4), d6 = Qvk3Ui.dp(a, 6);
        bar.setPadding(d4, d6, d4, d6);
        bar.setBackgroundColor(p.surface);
        bar.addView(Qvk3Ui.icon(a, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> d.dismiss()));
        TextView t = Qvk3Ui.line(a, title, 19, p.text, true);
        t.setPadding(d6, 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return bar;
    }

    private TextView section(String text) {
        TextView t = Qvk3Ui.line(a, text.toUpperCase(), 12, p.accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(20), dp(14), dp(20), dp(6));
        col.addView(t);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(p.surface);
        c.setBackground(bg);
        c.setClipToOutline(true);
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(12), 0, dp(12), 0);
        return lp;
    }

    private View divider() {
        View v = new View(a);
        v.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
        lp.leftMargin = dp(60);
        v.setLayoutParams(lp);
        return v;
    }

    private View row(String icon, String title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setMinimumHeight(dp(54));
        r.setBackground(Qvk3Ui.ripple(a, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Qvk3Icons.of(icon, p.accent));
        r.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(20), 0, 0, 0);
        tx.addView(Qvk3Ui.line(a, title, 16, p.text, false));
        if (sub != null) tx.addView(Qvk3Ui.line(a, sub, 13, p.text2, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chev = new ImageView(a);
        chev.setImageDrawable(Qvk3Icons.of(Qvk3Icons.RIGHT, (p.text2 & 0x00FFFFFF) | 0x88000000));
        r.addView(chev, new LinearLayout.LayoutParams(dp(16), dp(16)));
        r.setOnClickListener(click);
        return r;
    }

    private View dangerRow(String title, View.OnClickListener click) {
        TextView t = Qvk3Ui.line(a, title, 16, 0xFFE64646, false);
        t.setPadding(dp(58), dp(15), dp(14), dp(15));
        t.setBackground(Qvk3Ui.ripple(a, 0, 0x22E64646, 0));
        t.setOnClickListener(click);
        return t;
    }

    private View action(String icon, String label, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(dp(4), dp(4), dp(4), dp(4));
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Qvk3Icons.of(icon, p.accent));
        int pad = dp(13);
        ic.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(p.surface);
        ic.setBackground(bg);
        b.addView(ic, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView t = Qvk3Ui.line(a, label, 12, p.text2, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        b.addView(t);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        b.setLayoutParams(lp);
        return b;
    }

    private void confirm(String title, String msg, String yes, Runnable go) {
        Qvk3Ui.dialog(a).setTitle(title).setMessage(msg).setPositiveButton(yes, (d, w) -> go.run()).setNegativeButton("Отмена", null).show();
    }

    interface Call {
        void run() throws Exception;
    }

    private void run(Call call, Runnable after) {
        Qvk3Jobs.io(() -> {
            try {
                call.run();
                Qvk3Jobs.main(after);
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не удалось: " + Qvk3Jobs.message(e)));
            }
        });
    }

    private static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        String w = m10 == 1 && m100 != 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many;
        return n + " " + w;
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }
}
