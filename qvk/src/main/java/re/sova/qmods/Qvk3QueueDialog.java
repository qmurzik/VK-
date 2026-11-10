package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Queue manager: reorder (move up/down — reliable with TalkBack, unlike drag-only UIs), remove, play next,
 * shuffle a selected range, save as a local playlist, undo last edit, and listening history.
 * The queue lives in the engine, not in this dialog, so closing it loses nothing.
 */
public final class Qvk3QueueDialog {
    public static void show(Activity a) {
        Qvk3MusicEngine e = Qvk3MusicEngine.get(a);
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, Qvk3MusicSettings.layout(a));
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Qvk3Ui.dp(a, 12), 0, Qvk3Ui.dp(a, 12), 0);
        ListView list = new ListView(a);
        BaseAdapter adapter = new BaseAdapter() {
            List<Qvk3Track> items = e.queue().items();

            @Override
            public void notifyDataSetChanged() {
                items = e.queue().items();
                super.notifyDataSetChanged();
            }

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
                Qvk3Track t = items.get(i);
                LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : new LinearLayout(a);
                row.removeAllViews();
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 8));
                boolean cur = i == e.queue().currentIndex();
                row.addView(Qvk3Ui.line(a, (cur ? "▶ " : "") + t.title, 15, cur ? p.accent : p.text, cur));
                row.addView(Qvk3Ui.line(a, t.artist + " · " + Qvk3Track.time(t.duration * 1000L), 13, p.text2, false));
                row.setContentDescription((cur ? "Сейчас играет: " : "") + t.label());
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, i, id) -> {
            e.playIndex(i);
            list.postDelayed(adapter::notifyDataSetChanged, 200);
        });
        list.setOnItemLongClickListener((parent, view, i, id) -> {
            String[] actions = {"Играть следующим", "Поднять выше", "Опустить ниже", "Удалить из очереди", "Перемешать отсюда до конца"};
            Qvk3Ui.dialog(a).setTitle(e.queue().items().get(i).label()).setItems(actions, (d, w) -> {
                switch (w) {
                    case 0:
                        e.editQueue(() -> {
                            Qvk3Track t = e.queue().items().get(i);
                            int cur = e.queue().currentIndex();
                            e.queue().move(i, i > cur ? cur + 1 : cur);
                        });
                        break;
                    case 1:
                        e.editQueue(() -> e.queue().move(i, Math.max(0, i - 1)));
                        break;
                    case 2:
                        e.editQueue(() -> e.queue().move(i, Math.min(e.queue().size() - 1, i + 1)));
                        break;
                    case 3:
                        e.editQueue(() -> e.queue().remove(i));
                        break;
                    default:
                        e.editQueue(() -> e.queue().shuffleRange(i, e.queue().size() - 1, System.nanoTime()));
                }
                list.postDelayed(adapter::notifyDataSetChanged, 200);
            }).show();
            return true;
        });
        bar.addView(Qvk3Ui.button(a, p, "Отменить", false, v -> {
            e.editQueue(() -> {
                if (!e.queue().undo()) Qvk3Ui.toast(a, "Нечего отменять");
            });
            list.postDelayed(adapter::notifyDataSetChanged, 200);
        }));
        bar.addView(Qvk3Ui.button(a, p, "Сохранить как плейлист", false, v -> {
            EditText in = new EditText(a);
            in.setInputType(InputType.TYPE_CLASS_TEXT);
            in.setHint("Название");
            Qvk3Ui.dialog(a).setTitle("Плейлист QVK").setView(in).setPositiveButton("Сохранить", (d, w) -> {
                String name = in.getText().toString().trim();
                List<Qvk3Track> items = e.queue().items();
                Qvk3Jobs.io(() -> {
                    Qvk3MusicLibrary.get(a).saveLocalPlaylist(name.isEmpty() ? "Очередь" : name, items);
                    Qvk3Ui.toast(a, "Сохранено: " + items.size() + " треков");
                });
            }).setNegativeButton("Отмена", null).show();
        }));
        bar.addView(Qvk3Ui.button(a, p, "История", false, v -> history(a, p)));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.addView(bar);
        root.addView(hs);
        root.addView(list, new LinearLayout.LayoutParams(-1, Qvk3Ui.dp(a, 420)));
        int cur = e.queue().currentIndex();
        if (cur > 2) list.setSelection(cur - 2);
        AlertDialog d = Qvk3Ui.dialog(a).setTitle("Очередь · " + e.queue().size()).setView(root)
                .setPositiveButton("Закрыть", null).create();
        d.show();
    }

    static void history(Activity a, Qvk3Ui.Palette p) {
        List<Qvk3Track> h = Qvk3MusicLibrary.get(a).recent(100);
        if (h.isEmpty()) {
            Qvk3Ui.toast(a, Qvk3MusicSettings.history(a) ? "История пуста" : "История отключена в настройках QVK Music");
            return;
        }
        String[] labels = new String[h.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = h.get(i).label();
        Qvk3Ui.dialog(a).setTitle("Недавно прослушанные").setItems(labels, (d, i) -> Qvk3MusicEngine.get(a).play(h, i)).show();
    }
}
