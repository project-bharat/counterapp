# Japa Counter

Marathi-first native Android app for mantra and Japa counting. Built with Kotlin, Jetpack Compose Material 3, Room, MVVM and StateFlow.

## Japa counting
- Daily count tracking and history, with multiple counters/categories.
- Mantra-specific daily totals and all-time/weekly statistics.
- Manual counting and optional on-device voice counting.
- Offline speech models are downloaded on first use; speech processing stays on-device.
- Daily reminders, home-screen widget, backup/restore and CSV export.

## Privacy
Microphone access is only used when the user starts voice counting. Audio is processed in memory and not saved as recordings. Voice count is experimental and may misrecognize words; verify counts.

## Build
Install JDK 17 and Android SDK, then run:
```sh
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download APK from GitHub Actions
1. Open **Actions** and select **Android Debug APK**.
2. Open the latest successful run.
3. Download the **counterapp-debug-apk** artifact and extract `app-debug.apk`.

Every push to `main` triggers the build workflow.

## Launcher icon
Editable adaptive launcher resources are in `app/src/main/res`.