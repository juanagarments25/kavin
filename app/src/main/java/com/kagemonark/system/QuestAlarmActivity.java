package com.kagemonark.system;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen Quest Panel shown over the lock screen when a quest is due. */
public class QuestAlarmActivity extends Activity {
    private static WeakReference<QuestAlarmActivity> showing = new WeakReference<>(null);
    private JSONObject item;
    private TextToSpeech tts;
    private boolean answered;

    static void closeIfShowing() {
        QuestAlarmActivity a = showing.get();
        if (a != null) a.runOnUiThread(a::finish);
    }

    static int accentFor(JSONObject item) {
        if (item.optBoolean("missed") || "daily-due".equals(item.optString("type"))) return Color.parseColor("#FF3B4E");
        switch (item.optString("rank")) {
            case "E": return Color.parseColor("#4DA3FF");
            case "D": return Color.parseColor("#3FE0FF");
            case "C": return Color.parseColor("#FF2A4A");
            case "B": return Color.parseColor("#FF8A2A");
            case "A": return Color.parseColor("#7DF9FF");
            case "S": return Color.parseColor("#B05CFF");
            default:  return Color.parseColor("#3FE0FF");
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showing = new WeakReference<>(this);
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        load(intent);
    }

    private void load(Intent intent) {
        try { item = new JSONObject(intent.getStringExtra("item")); }
        catch (Exception e) { finish(); return; }
        answered = false;
        build();
        speak();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(spacing);
        t.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private void build() {
        int accent = accentFor(item);
        boolean daily = "daily".equals(item.optString("kind"));
        boolean warn = item.optBoolean("missed") || "daily-due".equals(item.optString("type"));
        int white = Color.parseColor("#EAF6FF"), dim = Color.parseColor("#9FB3C8");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding((int) dp(18), (int) dp(24), (int) dp(18), (int) dp(24));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#05070F"), Color.parseColor("#0A1020"), Color.parseColor("#05070F")});
        root.setBackground(bg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding((int) dp(20), (int) dp(22), (int) dp(20), (int) dp(20));
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.argb(235, 8, 14, 30));
        frame.setStroke((int) dp(2), accent);
        frame.setCornerRadius(dp(6));
        panel.setBackground(frame);
        panel.setElevation(dp(12));

        panel.addView(text(warn ? "⚠ SYSTEM WARNING" : "◈ KAGE MONARK SYSTEM", 13, accent, true, 0.25f));
        TextView headline = text(warn ? "QUEST INCOMPLETE" : (daily ? "DAILY QUEST" : "QUEST ARRIVED"), 30, white, true, 0.12f);
        headline.setShadowLayer(dp(10), 0, 0, accent);
        headline.setPadding(0, (int) dp(6), 0, (int) dp(10));
        panel.addView(headline);

        View line = new View(this);
        line.setBackgroundColor(accent);
        panel.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) dp(1)));

        if (!daily) {
            TextView name = text(item.optString("title"), 22, white, true, 0.02f);
            name.setPadding(0, (int) dp(14), 0, (int) dp(2));
            panel.addView(name);
        }
        panel.addView(text(item.optString("label"), 12, accent, true, 0.18f));

        String desc = item.optString("desc");
        if (!TextUtils.isEmpty(desc)) {
            TextView d = text(desc, 15, dim, false, 0f);
            d.setPadding(0, (int) dp(12), 0, 0);
            panel.addView(d);
        }

        int xp = item.optInt("xp");
        TextView reward = text(xp > 0 ? "+" + xp + " XP REWARD" : "REWARD ALREADY ACQUIRED", 16, Color.parseColor("#FFD54A"), true, 0.15f);
        reward.setPadding(0, (int) dp(14), 0, (int) dp(6));
        panel.addView(reward);

        String when = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date(item.optLong("due")));
        panel.addView(text("SCHEDULED  " + when + "     " + item.optString("level"), 11, dim, false, 0.12f));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(0, (int) dp(18), 0, 0);
        if (!daily) btns.addView(button("COMPLETE QUEST", accent, true, "done"));
        btns.addView(button("SNOOZE · 10 MIN", accent, false, "snooze"));
        btns.addView(button("DISMISS", Color.parseColor("#5A6B80"), false, "dismiss"));
        panel.addView(btns, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView open = text("OPEN KAGE MONARK", 12, dim, false, 0.2f);
        open.setPadding(0, (int) dp(14), 0, 0);
        open.setOnClickListener(v -> answer("open"));
        panel.addView(open);

        root.addView(panel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(bg);
        sv.addView(root);
        setContentView(sv);

        panel.setScaleY(0.05f);
        panel.animate().scaleY(1f).setDuration(420).start();
        ObjectAnimator glow = ObjectAnimator.ofFloat(headline, "alpha", 1f, 0.55f);
        glow.setDuration(900);
        glow.setRepeatMode(ValueAnimator.REVERSE);
        glow.setRepeatCount(ValueAnimator.INFINITE);
        glow.start();
    }

    private Button button(String label, int color, boolean filled, String action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(true);
        b.setLetterSpacing(0.15f);
        b.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(filled ? Color.parseColor("#05070F") : color);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(4));
        g.setStroke((int) dp(1.5f), color);
        g.setColor(filled ? color : Color.TRANSPARENT);
        b.setBackground(g);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) dp(52));
        lp.topMargin = (int) dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> answer(action));
        return b;
    }

    private void answer(String action) {
        if (answered) return;
        answered = true;
        if (tts != null) tts.stop();
        if ("open".equals(action)) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked() && Build.VERSION.SDK_INT >= 26) {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { ActionHandler.handle(QuestAlarmActivity.this, item, "open"); finish(); }
                    @Override public void onDismissCancelled() { answered = false; }
                    @Override public void onDismissError() { answered = false; }
                });
                return;
            }
        }
        ActionHandler.handle(this, item, action);
        finish();
    }

    private void speak() {
        final String line = buildVoice();
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, "kage");
            });
        } else {
            tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, "kage");
        }
    }

    private String buildVoice() {
        boolean daily = "daily".equals(item.optString("kind"));
        boolean warn = item.optBoolean("missed") || "daily-due".equals(item.optString("type"));
        String s = (warn ? "Warning. " : "") + (daily ? "Daily quest. " : "Quest arrived. ") + (daily ? "" : item.optString("title") + ". ");
        String d = item.optString("desc");
        if (!daily && !TextUtils.isEmpty(d) && d.length() < 160) s += d;
        return s;
    }

    @Override
    public void onBackPressed() {
        // Back does not silence the alarm; choose a button.
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (showing.get() == this) showing = new WeakReference<>(null);
        super.onDestroy();
    }
}
