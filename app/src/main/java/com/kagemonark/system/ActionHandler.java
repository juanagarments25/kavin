package com.kagemonark.system;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.json.JSONObject;

/** COMPLETE QUEST / SNOOZE / DISMISS / OPEN, from the alarm screen or the notification buttons. */
final class ActionHandler {
    private ActionHandler() {}

    static void handle(Context c, JSONObject item, String action) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(AlarmReceiver.notifId(item));
        QuestAlarmActivity.closeIfShowing();

        long until = 0;
        if ("snooze".equals(action)) until = AlarmScheduler.snooze(c, item);

        try {
            JSONObject a = new JSONObject();
            a.put("kind", item.optString("kind"));
            a.put("ref", item.optString("ref"));
            a.put("type", item.optString("type"));
            a.put("action", action);
            a.put("at", item.optLong("due"));
            if (until > 0) a.put("until", until);
            a.put("time", System.currentTimeMillis());
            AlarmStore.addAction(c, a);
        } catch (Exception ignored) {}

        if ("done".equals(action)) {
            int xp = item.optInt("xp");
            Toast.makeText(c, "QUEST COMPLETED" + (xp > 0 ? " · +" + xp + " XP" : ""), Toast.LENGTH_SHORT).show();
        } else if ("snooze".equals(action)) {
            Toast.makeText(c, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
        }

        if ("open".equals(action)) {
            Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        } else {
            MainActivity.drainIfLive();
        }
    }
}
