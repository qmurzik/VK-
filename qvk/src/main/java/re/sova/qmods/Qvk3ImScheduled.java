package re.sova.qmods;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * QVK 3.1: scheduled messages in the QVK messenger. VK's API has no delayed send for messages, so QVK keeps the text
 * on the phone and an Android alarm sends it at the chosen time (the phone must be on and online; if sending fails
 * QVK retries every 5 minutes, six times, and then says so in a notification).
 */
final class Qvk3ImScheduled {
    private Qvk3ImScheduled() {
    }

    static final String PREF = "qvk3_im_scheduled";
    private static final int MAX_TRIES = 6;

    static final class Item {
        long id, peer, at;
        String text, title;
        int tries;

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("peer", peer).put("at", at).put("text", text).put("title", title).put("tries", tries);
        }

        static Item of(JSONObject o) {
            Item i = new Item();
            i.id = o.optLong("id");
            i.peer = o.optLong("peer");
            i.at = o.optLong("at");
            i.text = o.optString("text");
            i.title = o.optString("title");
            i.tries = o.optInt("tries");
            return i;
        }
    }

    static synchronized List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Qvk3MusicSettings.prefs(c).getString(PREF, "[]"));
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(Item.of(a.optJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static List<Item> forPeer(Context c, long peer) {
        List<Item> out = new ArrayList<>();
        for (Item i : all(c)) if (i.peer == peer) out.add(i);
        return out;
    }

    private static synchronized void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) a.put(i.json());
        } catch (Exception ignored) {
        }
        Qvk3MusicSettings.prefs(c).edit().putString(PREF, a.toString()).apply();
    }

    static void add(Context c, long peer, String title, String text, long at) {
        List<Item> items = all(c);
        Item i = new Item();
        i.id = System.currentTimeMillis();
        i.peer = peer;
        i.title = title == null ? "" : title;
        i.text = text;
        i.at = at;
        items.add(i);
        save(c, items);
        arm(c);
    }

    static void cancel(Context c, long id) {
        List<Item> items = all(c);
        for (int k = items.size() - 1; k >= 0; k--) if (items.get(k).id == id) items.remove(k);
        save(c, items);
        arm(c);
    }

    /** One alarm for the earliest pending message (re-armed after each send, at start and after a reboot). */
    static void arm(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, 7301, new Intent(c, Qvk3ImScheduledReceiver.class)
                    .setAction(Qvk3ImScheduledReceiver.ACTION), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long next = Long.MAX_VALUE;
            for (Item i : all(c)) next = Math.min(next, i.at);
            if (next == Long.MAX_VALUE) {
                am.cancel(pi);
                return;
            }
            next = Math.max(next, System.currentTimeMillis() + 1000);
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);   // without the exact-alarm right: within minutes
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.im.scheduled.arm", t);
        }
    }

    /** Sends everything that is due (worker thread). */
    static void sendDue(Context c) {
        List<Item> items = all(c);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Item i : new ArrayList<>(items)) {
            if (i.at > now + 30_000) continue;
            try {
                Qvk3ImApi.send(i.peer, (System.nanoTime() & 0x3fffffffL) + 1, i.text, 0, null, null);
                items.remove(i);
                changed = true;
            } catch (Exception e) {
                i.tries++;
                changed = true;
                if (i.tries >= MAX_TRIES) {
                    items.remove(i);
                    notifyFailed(c, i, Qvk3Jobs.message(e));
                } else {
                    i.at = now + 5 * 60_000L;
                }
            }
        }
        if (changed) save(c, items);
        arm(c);
    }

    private static void notifyFailed(Context c, Item i, String why) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("qvk3_im_scheduled", "QVK · отложенные сообщения", NotificationManager.IMPORTANCE_DEFAULT));
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, "qvk3_im_scheduled")
                    : new android.app.Notification.Builder(c);
            String text = "«" + (i.text.length() > 60 ? i.text.substring(0, 60) + "…" : i.text) + "» — " + why;
            b.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Не отправлено" + (i.title.isEmpty() ? "" : ": " + i.title))
                    .setContentText(text).setStyle(new android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
            nm.notify((int) (i.id & 0x7fffffff), b.build());
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.im.scheduled.notify", t);
        }
    }

    static String when(long at) {
        Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
        c.setTimeInMillis(at);
        String time = String.format(java.util.Locale.ROOT, "%d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return "сегодня в " + time;
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return "завтра в " + time;
        return new java.text.SimpleDateFormat("d MMMM в H:mm", new java.util.Locale("ru")).format(new java.util.Date(at));
    }

    /** «Отправить позже»: quick choices or an exact date and time; then(at) gets the chosen moment. */
    static void pick(Activity a, java.util.function.LongConsumer then) {
        Calendar evening = Calendar.getInstance();
        evening.set(Calendar.HOUR_OF_DAY, 20);
        evening.set(Calendar.MINUTE, 0);
        evening.set(Calendar.SECOND, 0);
        if (evening.getTimeInMillis() < System.currentTimeMillis() + 60_000) evening.add(Calendar.DAY_OF_YEAR, 1);
        Calendar morning = Calendar.getInstance();
        morning.add(Calendar.DAY_OF_YEAR, 1);
        morning.set(Calendar.HOUR_OF_DAY, 9);
        morning.set(Calendar.MINUTE, 0);
        morning.set(Calendar.SECOND, 0);
        long hour = System.currentTimeMillis() + 60 * 60_000L;
        final long[] at = {hour, evening.getTimeInMillis(), morning.getTimeInMillis()};
        String[] labels = {"Через час", "Вечером · " + when(at[1]), "Утром · " + when(at[2]), "Выбрать дату и время…"};
        Qvk3Ui.dialog(a).setTitle("Отправить позже").setItems(labels, (d, w) -> {
            if (w < 3) {
                then.accept(at[w]);
                return;
            }
            Calendar c = Calendar.getInstance();
            new android.app.DatePickerDialog(a, (v, y, m, day) -> new android.app.TimePickerDialog(a, (tv, h, min) -> {
                Calendar r = Calendar.getInstance();
                r.set(y, m, day, h, min, 0);
                if (r.getTimeInMillis() < System.currentTimeMillis() + 30_000) {
                    Qvk3Ui.toast(a, "Это время уже прошло");
                    return;
                }
                then.accept(r.getTimeInMillis());
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        }).show();
    }
}
