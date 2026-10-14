package com.kagemonark.system;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small persistent store: the alarm list from the web app, and button presses waiting for the web app. */
final class AlarmStore {
    private static final String PREFS = "kage_monark";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ACTIONS = "actions";

    private AlarmStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized JSONArray items(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY_ITEMS, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    static synchronized void saveItems(Context c, JSONArray items) {
        prefs(c).edit().putString(KEY_ITEMS, items.toString()).apply();
    }

    static synchronized void addAction(Context c, JSONObject action) {
        JSONArray list;
        try { list = new JSONArray(prefs(c).getString(KEY_ACTIONS, "[]")); }
        catch (JSONException e) { list = new JSONArray(); }
        list.put(action);
        prefs(c).edit().putString(KEY_ACTIONS, list.toString()).apply();
    }

    static synchronized String takeActions(Context c) {
        String s = prefs(c).getString(KEY_ACTIONS, "[]");
        prefs(c).edit().putString(KEY_ACTIONS, "[]").apply();
        return s;
    }

    static synchronized boolean getFlag(Context c, String key) {
        return prefs(c).getBoolean(key, false);
    }

    static synchronized void setFlag(Context c, String key) {
        prefs(c).edit().putBoolean(key, true).apply();
    }
}
