# KAGE MONARK SYSTEM — Android

The KAGE MONARK SYSTEM web app inside an Android app, with real Android alarms:
quests, reminders and the daily quest ring at the exact time even when the app is closed,
and a full-screen Quest Panel (COMPLETE QUEST / SNOOZE / DISMISS) shows over the lock screen.

**Get the app:** open the **Releases** section of this repository and download `KAGE-MONARK-SYSTEM.apk`.

Every push to `main` builds a new APK automatically (GitHub Actions).

## Project layout
- `app/` — Android app (Java). Alarms: `AlarmScheduler`, `AlarmReceiver`; full-screen panel: `QuestAlarmActivity`; bridge `window.KageNative`: `KageBridge`.
- `web/` — source of the KAGE MONARK SYSTEM web app (`parts/` + `build.sh`). Edit `web/parts/*`, run `sh web/build.sh`,
  then copy `web/site/jarvis-shadow-reminder.html` to `app/src/main/assets/www/index.html` and push — GitHub builds a new APK.
