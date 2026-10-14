# J.A.R.V.I.S — Shadow Monarch Reminder System (v2.1)

A single-file web app. Everything (HTML, CSS, JavaScript, artwork) lives in `JARVIS_Shadow_Reminder.html`.
Data is saved in your browser's local storage. Nothing is sent to a server.

## Open it

**Online:** use the hosted link from the chat. It opens in any phone or desktop browser.
The link is private to your Claude account until you share it from the page's Share menu.

**Offline / local file:** double-click `JARVIS_Shadow_Reminder.html` (Chrome, Edge, Firefox or Safari).
On Android, copy it to the phone and open it with Chrome.

The hosted copy and the local file keep **separate** data (each one has its own browser storage).
Use Profile → Export / Import to move reminders, quests and XP between them.

## Enable browser notifications

1. Click **Enable Notifications** (top right) and choose **Allow** in the browser prompt.
2. Click the button again. It now says "Notifications On" and sends a test notification.
3. If it says **Notifications Blocked**: open the browser's site settings (padlock icon in the address bar),
   set Notifications to Allow, then reload the page.
4. Click **Test Voice** once. Browsers only allow speech after you've interacted with the page.

Notes:
- System notifications need the page served over HTTPS or opened as a local file.
- Some phone browsers (Chrome on Android) don't allow pop-up notifications from a normal web page.
  You still get the on-screen alert, chime and spoken voice while the page is open. Diagnostics shows
  "Allowed · page alerts only" when this happens.
- The hosted page runs inside Claude's secure frame, which may refuse notification permission.
  If so, the in-page alert and voice still work. For system notifications, use the local HTML file.

## What alerts can and can't do

- Reminders are checked every 10 seconds while the page is open. A background tab is fine,
  though browsers may slow checks to about once a minute.
- **If the browser or tab is fully closed, nothing can fire.** That's a browser limitation, not a setting.
- Reminders that came due while the page was closed alert as "MISSED" the next time you open it.
- Each reminder alerts once. Snooze reschedules it 10 minutes later.

## Features

- Reminders: title, date, time and notes. Sorted by time. Filters: All / Pending / Due / Completed.
  Complete, reopen, edit, delete (with a confirm step) and clear completed. Due reminders glow.
- Alerts: on-screen system window, chime, text-to-speech (choose the voice and rate in Profile) and a browser notification.
- Hunter profile: display name ("WELCOME BACK, NAME"), voice settings, backup export and import.
- Quests: Daily (10 XP, resets at midnight, once per day), Main (50 XP, once), Side (20 XP, once).
  Level and rank (E→S), XP bar, completion and level-up animations. Reopening a quest never removes XP,
  and completing it again never adds more.
- Diagnostics: real values only. Connection, notification permission, voice engine, battery (where the
  browser exposes it; not Safari or Firefox), screen size, storage and the reminder watcher.
  AI connection shows **Not configured** because no AI service is connected.

## Changes in v2.1

- Fixed a startup crash: the script stopped partway on load in Chrome, which left the date/time empty
  and broke quests, saving and alert closing.
- Fixed a crash every time a reminder alert fired (title flashing).
- Voice diagnostic now reports "No voices installed" instead of loading forever.
- Export copies the backup to the clipboard when running on the hosted page, where downloads are blocked.
- Existing data is kept. The storage keys are unchanged, and older reminder keys are imported once.

## Putting this into the JARVIS Android APK later

Two routes, depending on your project:

1. **Fast (WebView):** put the HTML in `app/src/main/assets/jarvis/index.html` and show it in a Compose
   `AndroidView { WebView(it) }` with `settings.javaScriptEnabled = true` and `settings.domStorageEnabled = true`,
   then `loadUrl("file:///android_asset/jarvis/index.html")`. Storage works.
   Web notifications and Web Speech do **not** work reliably inside WebView. Add a small `@JavascriptInterface` bridge
   (for example `Android.schedule(id, title, epochMs)` and `Android.speak(text)`) that calls Android
   `AlarmManager` / `NotificationManager` and `TextToSpeech`, so reminders fire even when the app is closed.
2. **Native (recommended for the real app):** keep the look and rebuild the screens in Jetpack Compose.
   Reminders go in Room, and alarms run through `AlarmManager.setExactAndAllowWhileIdle` plus a
   `BroadcastReceiver` that posts the notification and speaks it. Request
   `POST_NOTIFICATIONS` (Android 13+) and `SCHEDULE_EXACT_ALARM`. This is the only way to get alerts with the app closed.

Send the Android project when you're ready and it can be wired in either way.
