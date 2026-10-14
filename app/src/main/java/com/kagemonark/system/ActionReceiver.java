package com.kagemonark.system;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

/** Buttons on the alarm notification. */
public class ActionReceiver extends BroadcastReceiver {
    static PendingIntent pending(Context c, JSONObject item, String action) {
        Intent i = new Intent(c, ActionReceiver.class)
                .setAction("com.kagemonark.system.ACTION_" + action)
                .setData(Uri.parse("kage://action/" + action + "/" + Uri.encode(item.optString("id"))))
                .putExtra("item", item.toString())
                .putExtra("action", action);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            JSONObject item = new JSONObject(intent.getStringExtra("item"));
            ActionHandler.handle(context, item, intent.getStringExtra("action"));
        } catch (Exception ignored) {}
    }
}
