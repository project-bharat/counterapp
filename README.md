# CounterApp

Native Android daily counter built with Kotlin, Jetpack Compose Material 3, Room, MVVM and StateFlow. Marathi-first with an in-app English switch. Works offline.

## Build locally
Install JDK 17 and Android SDK, then run:
```sh
chmod +x ./gradlew
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download APK from GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Android Debug APK** and open the latest successful run.
3. Under **Artifacts**, download **counterapp-debug-apk**.
4. Extract the ZIP to get `app-debug.apk`.

Every push to `main` triggers the workflow. You can also start it manually with **Run workflow**.

## Launcher icon
The repository includes editable adaptive launcher vector resources inspired by the supplied blue-and-gold infinity mark. To use the exact artwork, export your square icon as PNG at 512×512, then replace the launcher resources with Android Studio **Image Asset** (right-click `app/src/main/res` → New → Image Asset → Launcher Icons (Adaptive and Legacy)). Keep the resource name `ic_launcher`.

## Baby movement records
Movement logging is a personal diary, not a fetal-wellbeing assessment. There is no universal target in this app. Contact your maternity-care professional promptly if the baby's usual movement pattern changes or movements reduce; do not wait for an app target or timer.
