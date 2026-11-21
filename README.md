# japaCounterApp

Marathi-first native Android Japa counter built with Kotlin, Jetpack Compose Material 3, Room, MVVM and StateFlow. Minimum Android 7.0 (API 24), tested build target Android 14 (API 34), with Android 15 foreground-service compatibility declarations.

## Automatic tracking

### Japa / mantra
- Uses Vosk on-device speech recognition in a microphone foreground service. Only final recognition segments are counted; the configured mantra is matched as a whole phrase, and the service writes counts to Room.
- The English (India) and Hindi (Devanagari) small models are downloaded on first use (approximately 36 MB and 42 MB). After installation, recognition runs locally without network access.
- The official Vosk model catalogue currently lists English and Hindi models but no Marathi model. The Devanagari option therefore uses the Hindi acoustic model as an experimental fallback; Marathi pronunciation accuracy is not guaranteed. Do not treat it as a reliable Marathi recognizer.
- Audio is processed in memory. This implementation does not save microphone recordings. Microphone access starts only after the user taps Start and grants permission. Android 14+ requires starting the microphone foreground service while the app is visible. The service is never auto-started after reboot.

## Permissions and background behaviour
- `RECORD_AUDIO`: requested when the user starts voice Japa.
- `POST_NOTIFICATIONS`: requested on Android 13+ so the user can see active tracking status.
- The user can stop voice counting from the app or its persistent notification.

## Build locally
Install JDK 17, Gradle 8.7 and Android SDK Platform 35, then run:
```sh
gradle testDebugUnitTest assembleDebug
``
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download APK from GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Android Debug APK** and open the latest successful run.
3. Under **Artifacts**, download **Japa-Counter-Build-<number>**.
4. Extract the artifact to get the numbered debug APK and a ZIP containing the same APK.

Every push to `main` or `japa-ui-recovery-528` triggers the workflow. You can also start it manually with **Run workflow**.

## Launcher icon
The repository includes editable adaptive launcher vector resources. To replace them, export a square 512×512 PNG and use Android Studio **Image Asset** (right-click `app/src/main/res` → New → Image Asset → Launcher Icons (Adaptive and Legacy)). Keep the resource name `ic_launcher`.

## Existing data and backups

The recovery branch hides the retired Baby Movement and Steps categories. Existing records are retained in Room and included in version 1 JSON backups. Restoring a backup replaces local records and creates a Japa category if the backup lacks one.
