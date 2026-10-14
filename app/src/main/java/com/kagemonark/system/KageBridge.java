package com.kagemonark.system;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** window.KageNative inside the web app. */
public class KageBridge {
    private final MainActivity activity;
    private final Context app;

    KageBridge(MainActivity a) {
        activity = a;
        app = a.getApplicationContext();
    }

    @JavascriptInterface
    public void schedule(String json) {
        try {
            AlarmScheduler.scheduleAll(app, new JSONArray(json));
            MainActivity.webReady = true;
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public String takeActions() {
        return AlarmStore.takeActions(app);
    }

    @JavascriptInterface
    public String status() {
        JSONObject o = new JSONObject();
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            o.put("notif", nm != null && nm.areNotificationsEnabled());
            o.put("exact", AlarmScheduler.canExact(app));
            o.put("fullScreen", Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent()));
            o.put("count", AlarmScheduler.count(app));
        } catch (Exception ignored) {}
        return o.toString();
    }

    @JavascriptInterface
    public void openSettings() {
        activity.runOnUiThread(activity::showAlarmSetup);
    }

    @JavascriptInterface
    public boolean saveFile(String name, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream os = app.getContentResolver().openOutputStream(uri)) {
                    if (os == null) return false;
                    os.write(data);
                }
                return true;
            } else {
                File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return false;
                try (FileOutputStream fo = new FileOutputStream(new File(dir, name))) { fo.write(data); }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
