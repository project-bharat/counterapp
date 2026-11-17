# CounterApp

Marathi-first native Android daily counter built with Kotlin, Jetpack Compose Material 3, Room, MVVM and StateFlow. Minimum Android 7.0 (API 24), tested build target Android 14 (API 34), with Android 15 foreground-service compatibility declarations.

## Automatic tracking

### Steps
- Uses Android `TYPE_STEP_COUNTER` as the authoritative cumulative step source and `TYPE_STEP_DETECTOR` for step-pulse feedback.
- The user explicitly starts or stops tracking. A persistent Health foreground-service notification remains visible while active.
- Daily sensor baselines are stored separately from Room count logs. The service detects counter resets after reboot and preserves the steps already logged for the current date.
- If the device has no step-counter sensor, automatic step counting is unavailable.

### Japa / mantra
- Uses Vosk on-device speech recognition in a microphone foreground service. Only final recognition segments are counted; the configured mantra is matched as a whole phrase, and the service writes counts to Room.
- The English (India) and Hindi (Devanagari) small models are downloaded on first use (approximately 36 MB and 42 MB). After installation, recognition runs locally without network access.
- The official Vosk model catalogue currently lists English and Hindi models but no Marathi model. The Devanagari option therefore uses the Hindi acoustic model as an experimental fallback; Marathi pronunciation accuracy is not guaranteed. Do not treat it as a reliable Marathi recognizer.
- Audio is processed in memory. This implementation does not save microphone recordings. Microphone access starts only after the user taps Start and grants permission. Android 14+ requires starting the microphone foreground service while the app is visible. The service is never auto-started after reboot.

### Experimental baby-movement assist
- Optional sensor experiment uses `TYPE_LINEAR_ACCELERATION` and `TYPE_GYROSCOPE`, a three-second baseline calibration, adaptive spike threshold and a cooldown to reduce repeated detections.
- It runs only while the Baby Movement screen is open and the user enables it. Manual tap counting remains available.
- Phone movement, posture changes, breathing and other vibration sources can trigger false positives or hide real movement. Smartphone sensors cannot reliably identify fetal movements. This is not a medical device, fetal monitor or substitute for manual counting or maternity-care advice.

## Permissions and background behaviour
- `ACTIVITY_RECOGNITION`: requested when the user starts automatic step tracking (Android 10+).
- `RECORD_AUDIO`: requested when the user starts voice Japa.
- `POST_NOTIFICATIONS`: requested on Android 13+ so the user can see active tracking status.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, and `FOREGROUND_SERVICE_MICROPHONE`: declared for Android foreground-service operation. These are normal install-time permissions; they do not grant sensor or microphone access.
- Step tracking can resume after reboot only if the user previously enabled it and Android still grants Activity Recognition. Microphone tracking never resumes automatically.
- The user can stop either service from the app or its persistent notification.

## Build locally
Install JDK 17 and Android SDK, then run:
```sh
chmod +x ./gradlew
./gradlew assembleDebug
``
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download APK from GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Android Debug APK** and open the latest successful run.
3. Under **Artifacts**, download **counterapp-debug-apk**.
4. Extract the ZIP to get `app-debug.apk`.

Every push to `main` triggers the workflow. You can also start it manually with **Run workflow**.

## Launcher icon
The repository includes editable adaptive launcher vector resources. To replace them, export a square 512×512 PNG and use Android Studio **Image Asset** (right-click `app/src/main/res` → New → Image Asset → Launcher Icons (Adaptive and Legacy)). Keep the resource name `ic_launcher`.

## Baby movement records
Movement logging is a personal diary, not a fetal-wellbeing assessment. There is no universal target in this app. Contact your maternity-care professional promptly if the baby's usual movement pattern changes or movements reduce; do not wait for an app target or timer.


## Walking step detection

The tracker prefers Android's hardware `TYPE_STEP_DETECTOR` and records each event immediately. It does not wait for a multi-step cadence warm-up or require a matching accelerometer peak, because those software gates add latency and can reject genuine steps. On devices without a step detector, it falls back to deltas from `TYPE_STEP_COUNTER`. The dashboard displays only the walking-step total; raw accelerometer samples are not displayed or counted as steps. Android sensor latency and accuracy remain device-dependent and must be validated with real-world walking tests.
