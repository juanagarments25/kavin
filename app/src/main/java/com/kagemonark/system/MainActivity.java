package com.kagemonark.system;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Hosts KAGE MONARK SYSTEM (the same web app) with native alarms underneath. */
public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String START = "https://" + HOST + "/index.html";
    private static final int REQ_FILE = 41, REQ_NOTIF = 42;

    private static WeakReference<MainActivity> live = new WeakReference<>(null);
    private static boolean resumed;
    static volatile boolean webReady;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    static boolean isLiveAndReady() {
        return resumed && webReady && live.get() != null;
    }

    static void runCheck() {
        MainActivity a = live.get();
        if (a != null) a.runOnUiThread(() -> a.js("window.checkAll&&checkAll()"));
    }

    static void drainIfLive() {
        MainActivity a = live.get();
        if (a != null && resumed) a.runOnUiThread(() -> a.js("window.kmNativeDrain&&kmNativeDrain()"));
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        live = new WeakReference<>(this);
        webReady = false;
        AlarmReceiver.ensureChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF05070F);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new KageBridge(this), "KageNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                try {
                    InputStream in = getAssets().open("www" + path);
                    WebResourceResponse r = new WebResourceResponse(mime(path), "UTF-8", in);
                    return r;
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "Choose backup file"), REQ_FILE); }
                catch (ActivityNotFoundException e) { fileCb = null; return false; }
                return true;
            }
        });

        setContentView(web);
        if (b != null) web.restoreState(b);
        else web.loadUrl(START);

        firstRunSetup();
    }

    private static String mime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    private void firstRunSetup() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            maybeShowSetup();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) maybeShowSetup();
    }

    private void maybeShowSetup() {
        if (!missingItems().isEmpty() && !AlarmStore.getFlag(this, "setup_shown")) {
            AlarmStore.setFlag(this, "setup_shown");
            showAlarmSetup();
        }
    }

    private List<String[]> missingItems() {
        List<String[]> out = new ArrayList<>();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && !nm.areNotificationsEnabled()) out.add(new String[]{"Allow notifications", "notif"});
        if (!AlarmScheduler.canExact(this)) out.add(new String[]{"Allow alarms & reminders", "exact"});
        if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent()) out.add(new String[]{"Allow full-screen Quest Panel", "fsi"});
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) out.add(new String[]{"Battery: don't restrict (recommended)", "battery"});
        return out;
    }

    /** One screen that opens each Android setting the alarms need. */
    void showAlarmSetup() {
        List<String[]> items = missingItems();
        if (items.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("KAGE MONARK SYSTEM")
                    .setMessage("All alarm settings are ready. Quests will ring even when the app is closed.\n\nAlarms armed: " + AlarmScheduler.count(this))
                    .setPositiveButton("OK", null)
                    .setNeutralButton("App settings", (d, w) -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle("Make quests ring when the app is closed")
                .setItems(labels, (d, which) -> fix(items.get(which)[1]))
                .setNegativeButton("Later", null)
                .show();
    }

    private void fix(String what) {
        String pkg = "package:" + getPackageName();
        switch (what) {
            case "notif":
                if (Build.VERSION.SDK_INT >= 26) open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                break;
            case "exact":
                if (Build.VERSION.SDK_INT >= 31) open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse(pkg)));
                break;
            case "fsi":
                if (Build.VERSION.SDK_INT >= 34) open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg)));
                break;
            case "battery":
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg)));
                break;
        }
    }

    private void open(Intent i) {
        try { startActivity(i); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            fileCb.onReceiveValue(u != null ? new Uri[]{u} : null);
            fileCb = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        live = new WeakReference<>(this);
        if (web != null) {
            web.onResume();
            js("window.kmNativeDrain&&kmNativeDrain();window.renderDiagnostics&&renderDiagnostics()");
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (live.get() == this) { live = new WeakReference<>(null); webReady = false; }
        if (web != null) { web.removeJavascriptInterface("KageNative"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
