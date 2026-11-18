package com.projectbharat.counterapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectbharat.counterapp.data.CounterDatabase
import com.projectbharat.counterapp.data.CounterLog
import com.projectbharat.counterapp.data.WalkingSession
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipInputStream

private const val ACTION_STOP = "com.projectbharat.counterapp.action.STOP_TRACKING"
const val TRACKING_STATE_CHANGED = "com.projectbharat.counterapp.TRACKING_STATE_CHANGED"
const val EXTRA_TRACKING_KIND = "tracking_kind"

private object TrackingNotifications {
    const val CHANNEL_ID = "active_tracking"
    const val STEP_ID = 7101
    const val CHANT_ID = 7102
    fun show(context: Context, id: Int, title: String, text: String, stopIntent: PendingIntent?): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible while automatic tracking is active"
                setShowBadge(false)
            }
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title).setContentText(text).setOngoing(true)
            .setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_my_calendar, "Start session",
                CounterQuickActions.activityPending(context, "BABY", 7103))
            .addAction(android.R.drawable.ic_media_play, "Start Japa",
                CounterQuickActions.activityPending(context, "MANTRA", 7104))
            .apply { if (stopIntent != null) addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent) }
            .build()
    }
    fun stopPending(context: Context, service: Class<out Service>, id: Int): PendingIntent =
        PendingIntent.getService(context, id, Intent(context, service).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!com.google.android.gms.location.ActivityTransitionResult.hasResult(intent)) return
        val result = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent) ?: return
        val prefs = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
        result.transitionEvents.forEach { event ->
            when {
                event.activityType == com.google.android.gms.location.DetectedActivity.ON_FOOT &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    prefs.edit().putBoolean("activity_on_foot", true).apply()
                event.activityType == com.google.android.gms.location.DetectedActivity.STILL &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    prefs.edit().putBoolean("activity_on_foot", false).apply()
                event.activityType == com.google.android.gms.location.DetectedActivity.IN_VEHICLE &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    prefs.edit().putBoolean("activity_on_foot", false).apply()
                event.activityType == com.google.android.gms.location.DetectedActivity.ON_BICYCLE &&
                    event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    prefs.edit().putBoolean("activity_on_foot", false).apply()
            }
        }
    }
}

class StepTrackingService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var gpsListener: LocationListener? = null
    private var categoryId = -1L
    private var today = ""
    @Volatile private var walkSessionId = -1L
    @Volatile private var activityTransitionsReady = false
    private var lastReliableLocation: Location? = null
    private lateinit var stepCounterDelta: StepCounterDeltaTracker
    private var transitionPendingIntent: PendingIntent? = null
    @Volatile private var serviceActive = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = getSharedPreferences("tracking_state", MODE_PRIVATE)
        if (intent?.action == ACTION_STOP) {
            state.edit().putBoolean("steps_enabled", false).putBoolean("steps_running", false)
                .putBoolean("activity_on_foot", false).apply()
            scope.launch {
                if (walkSessionId > 0L) CounterDatabase.get(this@StepTrackingService).dao()
                    .finishWalkingSession(walkSessionId, System.currentTimeMillis())
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        categoryId = intent?.getLongExtra(EXTRA_CATEGORY_ID, -1L) ?: state.getLong("step_category", -1L)
        if (categoryId <= 0 || (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            stopSelf(); return START_NOT_STICKY
        }
        if (serviceActive) return START_STICKY
        serviceActive = true
        state.edit().putLong("step_category", categoryId).putBoolean("steps_enabled", true).putBoolean("steps_running", true).apply()
        val notification = TrackingNotifications.show(this, TrackingNotifications.STEP_ID, "CounterApp · Steps",
            "Automatic daily step tracking is active", null)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, TrackingNotifications.STEP_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            ServiceCompat.startForeground(this, TrackingNotifications.STEP_ID, notification, 0)
        }

        // Prefer Android's cumulative hardware counter for reliable all-day totals.
        // Use the per-step detector only when the counter is unavailable or registration fails.
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector == null && stepCounter == null) {
            state.edit().putBoolean("steps_enabled", false).putBoolean("steps_running", false).apply()
            serviceActive = false
            stopSelf(); return START_NOT_STICKY
        }
        stepCounterDelta = StepCounterDeltaTracker(state.getInt("last_hardware_step_total", -1))
        today = LocalDate.now().toString()
        scope.launch {
            val dao = CounterDatabase.get(this@StepTrackingService).dao()
            val sessionStart = System.currentTimeMillis()
            dao.finishOpenWalkingSessions(categoryId, sessionStart)
            walkSessionId = dao.insertWalkingSession(WalkingSession(categoryId = categoryId, date = today, startedAt = sessionStart))

            // Register the cumulative counter first. Its readings continue to
            // advance while the app is backgrounded and avoid relying on activity transitions.
            val counterRegistered = stepCounter?.let {
                runCatching { sensorManager.registerListener(this@StepTrackingService, it, SensorManager.SENSOR_DELAY_NORMAL, 0) }.getOrDefault(false)
            } ?: false
            val detectorRegistered = if (!counterRegistered) {
                stepDetector?.let {
                    runCatching { sensorManager.registerListener(this@StepTrackingService, it, SensorManager.SENSOR_DELAY_NORMAL, 0) }.getOrDefault(false)
                } ?: false
            } else false
            if (!counterRegistered && !detectorRegistered) {
                Log.e("CounterAppSteps", "Neither hardware step sensor could be registered")
                dao.finishWalkingSession(walkSessionId, System.currentTimeMillis())
                state.edit().putBoolean("steps_enabled", false).putBoolean("steps_running", false).apply()
                serviceActive = false
                sendState(false)
                stopSelf()
                return@launch
            }
            state.edit().putString("step_sensor_source", if (counterRegistered) "TYPE_STEP_COUNTER" else "TYPE_STEP_DETECTOR").apply()

            // GPS is optional and is used only for distance; step counting never depends on it.
            if (ContextCompat.checkSelfPermission(this@StepTrackingService, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val now = System.currentTimeMillis()
                        var reliableDistance = 0.0
                        if (location.hasAccuracy() && location.accuracy <= 10f) {
                            val previous = lastReliableLocation
                            if (previous != null) {
                                val gap = now - previous.time
                                val segment = previous.distanceTo(location).toDouble()
                                val speed = if (gap > 0) segment / (gap / 1000.0) else Double.POSITIVE_INFINITY
                                if (gap in 1L..20_000L && segment in 2.0..40.0 && speed in 0.35..3.0) reliableDistance = segment
                            }
                            lastReliableLocation = Location(location)
                        } else lastReliableLocation = null
                        if (reliableDistance <= 0.0) return
                        scope.launch {
                            if (walkSessionId > 0L) CounterDatabase.get(this@StepTrackingService).dao()
                                .addWalkingMetrics(walkSessionId, 0, reliableDistance)
                        }
                    }
                    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                gpsListener = listener
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(listener::onLocationChanged)
                } catch (error: SecurityException) {
                    Log.w("CounterAppSteps", "Optional GPS distance is unavailable; step counting continues", error)
                }
            }
        }
        sendState(true)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            // The hardware detector already classifies steps. Avoid cadence warm-up
            // and accelerometer peak matching, which delay and discard real steps.
            recordSteps(1, "HARDWARE_STEP_DETECTOR")
            return
        }
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val delta = stepCounterDelta.update(event.values[0].toInt())
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit()
                .putInt("last_hardware_step_total", stepCounterDelta.lastValue).apply()
            if (delta > 0) recordSteps(delta, "HARDWARE_STEP_COUNTER")
        }
    }

    private fun recordSteps(steps: Int, source: String) {
        if (steps <= 0) return
        val now = System.currentTimeMillis()
        val dateNow = LocalDate.now().toString()
        scope.launch {
            val dao = CounterDatabase.get(this@StepTrackingService).dao()
            dao.addLog(CounterLog(date = dateNow, timestamp = now, categoryId = categoryId,
                count = steps, note = source))
            if (walkSessionId > 0L) dao.addWalkingMetrics(walkSessionId, steps, 0.0)
            sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
                .putExtra(EXTRA_TRACKING_KIND, "steps").putExtra("delta", steps))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        gpsListener?.let { runCatching { locationManager.removeUpdates(it) } }
        gpsListener = null
        serviceActive = false
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("steps_running", false).apply()
        sendState(false)
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun sendState(active: Boolean) = sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
        .putExtra(EXTRA_TRACKING_KIND, "steps").putExtra("active", active))

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        private const val ACTIVITY_TRANSITION_REQUEST_CODE = 7105
        private const val ACTION_ACTIVITY_TRANSITIONS = "com.projectbharat.counterapp.ACTIVITY_TRANSITIONS"
        fun start(context: Context, categoryId: Long) = ContextCompat.startForegroundService(
            context, Intent(context, StepTrackingService::class.java).putExtra(EXTRA_CATEGORY_ID, categoryId))
        fun stop(context: Context) { context.startService(Intent(context, StepTrackingService::class.java).setAction(ACTION_STOP)) }
    }
}
/** Reuses the loaded Vosk acoustic model between Japa sessions in this app process. */
private object VoskModelCache {
    private var cachedPath: String? = null
    private var cachedModel: Model? = null

    @Synchronized
    fun get(directory: File): Model {
        val path = directory.absolutePath
        if (cachedPath == path && cachedModel != null) return cachedModel!!
        cachedModel?.close()
        cachedModel = null
        cachedPath = null
        return Model(path).also {
            cachedModel = it
            cachedPath = path
        }
    }
}

class ChantRecognitionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var recording = false
    @Volatile private var stopRequested = false
    private var audioRecord: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var model: Model? = null
    private var worker: Thread? = null
    private var categoryId = -1L
    private var mantra = "Ram"
    private var language = "en"
    private var partialCounted = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecognition(); stopSelf(); return START_NOT_STICKY }
        stopRequested = false
        partialCounted = 0
        categoryId = intent?.getLongExtra(StepTrackingService.EXTRA_CATEGORY_ID, -1L) ?: -1L
        mantra = intent?.getStringExtra(EXTRA_MANTRA)?.trim().orEmpty().ifBlank { "Ram" }
        language = intent?.getStringExtra(EXTRA_LANGUAGE).orEmpty().ifBlank { "en" }
        if (categoryId <= 0 || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val notification = TrackingNotifications.show(this, TrackingNotifications.CHANT_ID, "CounterApp · Japa",
            "Microphone active for “" + mantra + "”",
            TrackingNotifications.stopPending(this, ChantRecognitionService::class.java, TrackingNotifications.CHANT_ID))
        if (Build.VERSION.SDK_INT >= 30) ServiceCompat.startForeground(this, TrackingNotifications.CHANT_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else ServiceCompat.startForeground(this, TrackingNotifications.CHANT_ID, notification, 0)
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", true).apply()
        sendState(true, "Preparing offline speech model")
        worker = Thread { runRecognition() }.also { it.name = "CounterApp-Vosk"; it.start() }
        return START_NOT_STICKY
    }

    private fun runRecognition() {
        try {
            val modelName = if (language == "hi") "vosk-model-small-hi-0.22" else "vosk-model-small-en-in-0.4"
            val modelUrl = if (language == "hi")
                "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
            else "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
            val modelDir = File(filesDir, modelName)
            if (!File(modelDir, "am/final.mdl").exists()) downloadAndExtractModel(modelUrl, modelName, modelDir)
            if (stopRequested || Thread.currentThread().isInterrupted) return
            sendState(true, "Loading offline speech model…")
            model = VoskModelCache.get(modelDir)
            val cleanMantra = mantra.replace(Regex("[।॥,;]"), " ").replace(Regex("\\s+"), " ").trim()
            val grammarPhrases = linkedSetOf(mantra, cleanMantra)
            // Allow consecutive repetitions to be decoded as one continuous utterance.
            if (cleanMantra.split(" ").size <= 14) {
                grammarPhrases.add("$cleanMantra $cleanMantra")
                grammarPhrases.add("$cleanMantra $cleanMantra $cleanMantra")
            }
            if (language == "hi") {
                grammarPhrases.add(cleanMantra.replace("ॐ", "ओम").replace("नमः", "नम"))
                grammarPhrases.add(cleanMantra.replace("त्र्यम्बकं", "त्र्यंबकं"))
                // Hindi Vosk commonly alternates anusvara spellings and joins Sanskrit compounds.
                if (cleanMantra.contains("दिगंबरा")) {
                    grammarPhrases.add(cleanMantra.replace("दिगंबरा", "दिगम्बरा"))
                    grammarPhrases.add(cleanMantra.replace("दिगंबरा", "दिगम्बरा").replace("श्रीपाद वल्लभ", "श्रीपादवल्लभ"))
                    grammarPhrases.add(cleanMantra.replace("श्रीपाद वल्लभ", "श्रीपादवल्लभ"))
                }
            } else {
                grammarPhrases.add(cleanMantra.replace("Aum", "Om").replace("aum", "om"))
                grammarPhrases.add(cleanMantra.replace("Shivaaya", "Shivaya").replace("shivaaya", "shivaya"))
            }
            val grammarJson = JSONArray()
            grammarPhrases.forEach { grammarJson.put(it) }
            grammarJson.put("[unk]")
            val grammar = grammarJson.toString()
            recognizer = Recognizer(model, 16000.0f, grammar)
            val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("Audio input is not supported")
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 8192))
            if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw IllegalStateException("Microphone unavailable") }
            audioRecord = record
            if (stopRequested || Thread.currentThread().isInterrupted) { record.release(); audioRecord = null; return }
            record.startRecording()
            recording = true
            sendState(true, "Offline model ready · listening offline")
            val buffer = ByteArray(maxOf(minBuffer, 8192))
            var lastPartial = ""
            while (recording && !Thread.currentThread().isInterrupted) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("Microphone read failed: " + read)
                if (read > 0) {
                    val activeRecognizer = recognizer ?: continue
                    if (activeRecognizer.acceptWaveForm(buffer, read)) {
                        val transcript = JSONObject(activeRecognizer.result ?: "{}").optString("text").trim()
                        val matches = countPhrase(transcript, mantra)
                        val delta = (matches - partialCounted).coerceAtLeast(0)
                        partialCounted = 0
                        sendRecognitionUpdate(transcript, delta)
                        lastPartial = ""
                    } else {
                        val partial = JSONObject(activeRecognizer.partialResult ?: "{}").optString("partial").trim()
                        if (partial != lastPartial) {
                            lastPartial = partial
                            val detected = countPhrase(partial, mantra)
                            val delta = (detected - partialCounted).coerceAtLeast(0)
                            if (detected > partialCounted) partialCounted = detected
                            sendRecognitionUpdate(partial, delta)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("CounterAppVosk", "Offline chant recognition stopped", error)
            if (!stopRequested) {
                sendState(false, error.message ?: "Speech engine error")
                stopSelf()
            }
        } finally {
            recording = false
            try { audioRecord?.stop() } catch (_: Exception) { }
            audioRecord?.release(); audioRecord = null
            recognizer?.close(); recognizer = null
            // VoskModelCache retains the acoustic model for the next Japa session.
        }
    }

    private fun sendRecognitionUpdate(transcript: String, matches: Int) {
        val matched = matches > 0 || countPhrase(transcript, mantra) > 0
        sendState(true, if (matched) "Mantra matched" else "Listening offline", transcript, matched)
        if (matches <= 0) return
        scope.launch {
            CounterDatabase.get(this@ChantRecognitionService).dao().incrementMantraDaily(
                categoryId, LocalDate.now().toString(), mantra, matches
            )
            sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
                .putExtra(EXTRA_TRACKING_KIND, "chant").putExtra("delta", matches)
                .putExtra("transcript", transcript).putExtra("matched", true))
        }
    }

    private fun countPhrase(transcript: String, phrase: String): Int {
        if (transcript.isBlank() || phrase.isBlank()) return 0
        val normalize = { value: String ->
            value.lowercase(Locale.ROOT)
                .replace("ॐ", "ओम")
                .replace("aum", "om")
                .replace("shivaaya", "shivaya")
                .replace("namaha", "namah")
                .replace("दिगम्बरा", "दिगंबरा")
                .replace("श्रीपादवल्लभ", "श्रीपाद वल्लभ")
                .replace("tryambakam", "tryambakam")
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ").trim()
        }
        val text = normalize(transcript)
        val target = normalize(phrase)
        if (target.isBlank()) return 0
        val exact = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(target) + "(?![\\p{L}\\p{N}])")
            .findAll(text).count()
        if (exact > 0) return exact

        // Vosk may return a close Sanskrit/Hindi spelling for a syllable.
        // Keep short mantras exact; for longer selections allow small token-level
        // spelling differences, but require a high ordered match from the first word.
        val expected = target.split(" ").filter { it.isNotBlank() }
        val spoken = text.split(" ").filter { it.isNotBlank() }
        if (expected.size < 3 || spoken.isEmpty()) return 0
        if (tokenSimilarity(expected.first(), spoken.first()) < 0.70) return 0

        var cursor = 0
        var matchedTokens = 0
        for (word in spoken) {
            var bestIndex = -1
            var bestScore = 0.0
            for (index in cursor until expected.size) {
                val score = tokenSimilarity(expected[index], word)
                if (score > bestScore) { bestScore = score; bestIndex = index }
            }
            if (bestIndex >= 0 && bestScore >= 0.70) {
                matchedTokens++
                cursor = bestIndex + 1
                if (cursor >= expected.size) break
            }
        }
        return if (matchedTokens.toDouble() / expected.size >= 0.78) 1 else 0
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isBlank() || second.isBlank()) return 0.0
        val previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)
        for (i in first.indices) {
            current[0] = i + 1
            for (j in second.indices) {
                val cost = if (first[i] == second[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        val distance = previous[second.length]
        return 1.0 - distance.toDouble() / maxOf(first.length, second.length)

    }

    private fun downloadAndExtractModel(modelUrl: String, modelName: String, target: File) {
        val selectedLanguage = if (modelName.contains("-hi-")) "hi" else "en"
        VoskOfflineModelInstaller.install(this, selectedLanguage) { status ->
            sendState(true, status)
        }
    }

    private fun stopRecognition() {
        stopRequested = true
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        worker?.interrupt()
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).apply()
        sendState(false, "Stopped")
    }
    private fun sendState(active: Boolean, message: String, transcript: String? = null, matched: Boolean? = null) {
        val intent = Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
            .putExtra(EXTRA_TRACKING_KIND, "chant").putExtra("active", active).putExtra("message", message)
        transcript?.let { intent.putExtra("transcript", it) }
        matched?.let { intent.putExtra("matched", it) }
        sendBroadcast(intent)
    }
    override fun onDestroy() {
        stopRequested = true
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        worker?.interrupt()
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).apply()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_MANTRA = "mantra"
        const val EXTRA_LANGUAGE = "language"
        fun start(context: Context, categoryId: Long, mantra: String, language: String) {
            val intent = Intent(context, ChantRecognitionService::class.java)
                .putExtra(StepTrackingService.EXTRA_CATEGORY_ID, categoryId)
                .putExtra(EXTRA_MANTRA, mantra).putExtra(EXTRA_LANGUAGE, language)
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, ChantRecognitionService::class.java)) }
    }
}

class TrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val state = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
        if (!state.getBoolean("steps_enabled", false)) return
        val categoryId = state.getLong("step_category", -1L)
        if (categoryId <= 0L) return
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        try { StepTrackingService.start(context, categoryId) } catch (error: Exception) {
            Log.w("CounterAppSteps", "Step service could not restart after reboot", error)
        }
    }
}
