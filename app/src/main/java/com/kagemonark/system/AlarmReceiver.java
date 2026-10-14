package com.kagemonark.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;

import org.json.JSONObject;

/** Fires when a quest is due: shows the full-screen Quest Panel and a ringing alarm notification. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "kage_quest_alarm_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        String json = intent.getStringExtra("item");
        if (json == null) return;
        try {
            JSONObject item = new JSONObject(json);
            AlarmScheduler.forget(context, item.optString("id"));
            if (MainActivity.isLiveAndReady()) {
                // App is open on screen: the app's own System Message panel shows it.
                MainActivity.runCheck();
                return;
            }
            fire(context, item);
        } catch (Exception ignored) {}
    }

    static int notifId(JSONObject item) {
        return (item.optString("kind") + ":" + item.optString("ref")).hashCode();
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Quest alarms", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Rings when a quest, reminder or daily quest is due");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 900});
        ch.enableLights(true);
        ch.setLightColor(Color.CYAN);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    static void fire(Context c, JSONObject item) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kage:alarm");
            wl.acquire(10_000L);
        }
        try {
            ensureChannel(c);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            int id = notifId(item);
            String json = item.toString();

            Intent full = new Intent(c, QuestAlarmActivity.class)
                    .putExtra("item", json)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent fullPi = PendingIntent.getActivity(c, id, full,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            boolean daily = "daily".equals(item.optString("kind"));
            boolean warn = item.optBoolean("missed") || "daily-due".equals(item.optString("type"));
            String head = warn ? "SYSTEM WARNING" : "KAGE MONARK SYSTEM";
            String title = item.optString("title");
            String label = item.optString("label");
            String desc = item.optString("desc");
            int xp = item.optInt("xp");

            Notification.Builder b = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_kage)
                    .setColor(QuestAlarmActivity.accentFor(item))
                    .setContentTitle(head + " · " + title)
                    .setContentText(label + (xp > 0 ? " · +" + xp + " XP" : ""))
                    .setStyle(new Notification.BigTextStyle().bigText(label + (xp > 0 ? " · +" + xp + " XP" : "") + (desc.isEmpty() ? "" : "\n" + desc)))
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullPi, true)
                    .setContentIntent(fullPi)
                    .setDeleteIntent(ActionReceiver.pending(c, item, "dismiss"))
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setWhen(item.optLong("due"))
                    .setShowWhen(true);
            if (!daily) b.addAction(new Notification.Action.Builder(null, "COMPLETE QUEST", ActionReceiver.pending(c, item, "done")).build());
            b.addAction(new Notification.Action.Builder(null, "SNOOZE 10 MIN", ActionReceiver.pending(c, item, "snooze")).build());
            b.addAction(new Notification.Action.Builder(null, "DISMISS", ActionReceiver.pending(c, item, "dismiss")).build());

            Notification n = b.build();
            n.flags |= Notification.FLAG_INSISTENT;   // keeps ringing until you answer
            nm.notify(id, n);
        } catch (SecurityException ignored) {
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
        }
    }
}
