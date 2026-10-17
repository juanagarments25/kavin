# QR Login — build the APK

This is a ready-to-build Android Studio project. It wraps the QR Login web app
(bundled in `app/src/main/assets/`) in a WebView, so it runs offline and the QR
scanner's camera works.

## Easiest: Android Studio
1. Install **Android Studio** (free). Open this `QRLogin-android` folder.
2. Let it sync (it downloads the Android SDK + Gradle automatically — first time only).
3. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
4. When it finishes, click **locate** → `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy that APK to your phone and open it (allow "install unknown apps").

## Command line (if the SDK is installed)
```bash
cd QRLogin-android
./gradlew assembleDebug      # first generate the wrapper: gradle wrapper --gradle-version 8.9
# → app/build/outputs/apk/debug/app-debug.apk
```

## Signed release APK
Android Studio ▸ **Build ▸ Generate Signed Bundle / APK ▸ APK**, create a keystore,
pick **release**. Keep the keystore safe — you need it for every future update.

## Change the app later
Edit `app/src/main/assets/index.html` (the web app). Rebuild. That's the whole app.

## Real SMS OTP
The bundled app runs in on-device demo mode until you add Firebase keys. Open
`app/src/main/assets/index.html`, fill the `FIREBASE_CONFIG` block (see notes in
that file), rebuild. Firebase Phone Auth then sends a real SMS.
