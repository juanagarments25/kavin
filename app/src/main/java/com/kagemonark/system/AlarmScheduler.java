package com.kagemonark.system;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Puts every upcoming quest / reminder / daily quest on Android's alarm clock, so it rings even when the app is closed. */
final class AlarmScheduler {
    static final String ACTION_ALARM = "com.kagemonark.system.ALARM";
    static final long SNOOZE_MS = 10 * 60 * 1000L;

    private AlarmScheduler() {}

    /** Replace the whole alarm list with the one sent by the web app. Native snoozes are kept. */
    static synchronized void scheduleAll(Context c, JSONArray incoming) {
        JSONArray old = AlarmStore.items(c);
        long now = System.currentTimeMillis();
        JSONArray next = new JSONArray();
        Set<String> keep = new HashSet<>();
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject it = incoming.optJSONObject(i);
            if (it == null || it.optLong("due") <= now) continue;
            next.put(it);
            keep.add(it.optString("id"));
        }
        for (int i = 0; i < old.length(); i++) {
            JSONObject it = old.optJSONObject(i);
            if (it == null) continue;
            String id = it.optString("id");
            if (it.optBoolean("nativeSnooze") && it.optLong("due") > now && !keep.contains(id)) {
                // A snooze made on the alarm screen stays until the app confirms it with its own item.
                if (!hasRef(next, it.optString("kind"), it.optString("ref"))) { next.put(it); keep.add(id); }
            }
        }
        for (int i = 0; i < old.length(); i++) {
            JSONObject it = old.optJSONObject(i);
            if (it != null && !keep.contains(it.optString("id"))) cancel(c, it.optString("id"));
        }
        AlarmStore.saveItems(c, next);
        for (int i = 0; i < next.length(); i++) set(c, next.optJSONObject(i));
    }

    private static boolean hasRef(JSONArray list, String kind, String ref) {
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it != null && kind.equals(it.optString("kind")) && ref.equals(it.optString("ref"))) return true;
        }
        return false;
    }

    /** After a reboot / time change / app update. */
    static synchronized void rescheduleFromStore(Context c) {
        JSONArray all = AlarmStore.items(c);
        long now = System.currentTimeMillis();
        JSONArray next = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.optJSONObject(i);
            if (it == null) continue;
            if (it.optLong("due") > now) { next.put(it); set(c, it); }
            else if (now - it.optLong("due") < 6 * 60 * 60 * 1000L) {
                // Missed while the phone was off: ring right away.
                try { it.put("missed", true); } catch (Exception ignored) {}
                AlarmReceiver.fire(c, it);
            }
        }
        AlarmStore.saveItems(c, next);
    }

    /** Snooze from the alarm screen: ring again in 10 minutes. */
    static synchronized long snooze(Context c, JSONObject item) {
        long until = System.currentTimeMillis() + SNOOZE_MS;
        try {
            JSONObject copy = new JSONObject(item.toString());
            copy.put("id", item.optString("id") + ":snz:" + until);
            copy.put("due", until);
            copy.put("nativeSnooze", true);
            copy.remove("missed");
            JSONArray all = AlarmStore.items(c);
            all.put(copy);
            AlarmStore.saveItems(c, all);
            set(c, copy);
        } catch (Exception ignored) {}
        return until;
    }

    static int count(Context c) {
        JSONArray all = AlarmStore.items(c);
        long now = System.currentTimeMillis();
        int n = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.optJSONObject(i);
            if (it != null && it.optLong("due") > now) n++;
        }
        return n;
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static PendingIntent alarmIntent(Context c, String id, String itemJson, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        i.setData(Uri.parse("kage://alarm/" + Uri.encode(id)));
        if (itemJson != null) i.putExtra("item", itemJson);
        return PendingIntent.getBroadcast(c, 0, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(Context c, JSONObject it) {
        if (it == null) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long due = it.optLong("due");
        PendingIntent pi = alarmIntent(c, it.optString("id"), it.toString(), PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (canExact(c)) {
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, 1, show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(due, showPi), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi);
        }
    }

    private static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, id, null, PendingIntent.FLAG_NO_CREATE);
        if (am != null && pi != null) { am.cancel(pi); pi.cancel(); }
    }

    /** Remove a fired item from the stored list. */
    static synchronized void forget(Context c, String id) {
        JSONArray all = AlarmStore.items(c);
        JSONArray next = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.optJSONObject(i);
            if (it != null && !id.equals(it.optString("id"))) next.put(it);
        }
        AlarmStore.saveItems(c, next);
    }
}
