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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectbharat.counterapp.data.CounterDatabase
import com.projectbharat.counterapp.data.CounterLog
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
    fun show(context: Context, id: Int, title: String, text: String, stopIntent: PendingIntent): Notification {
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
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent).build()
    }
    fun stopPending(context: Context, service: Class<out Service>, id: Int): PendingIntent =
        PendingIntent.getService(context, id, Intent(context, service).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class StepTrackingService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private val motionValidator = StepMotionValidator()
    private var categoryId = -1L
    private var today = ""
    @Volatile private var loggedToday = 0

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = getSharedPreferences("tracking_state", MODE_PRIVATE)
        if (intent?.action == ACTION_STOP) {
            state.edit().putBoolean("steps_enabled", false).putBoolean("steps_running", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        categoryId = intent?.getLongExtra(EXTRA_CATEGORY_ID, -1L) ?: state.getLong("step_category", -1L)
        if (categoryId <= 0 || (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            stopSelf(); return START_NOT_STICKY
        }
        state.edit().putLong("step_category", categoryId).putBoolean("steps_enabled", true).putBoolean("steps_running", true).apply()
        val notification = TrackingNotifications.show(this, TrackingNotifications.STEP_ID, "CounterApp · Steps",
            "Walking detection is active",
            TrackingNotifications.stopPending(this, StepTrackingService::class.java, TrackingNotifications.STEP_ID))
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, TrackingNotifications.STEP_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        else ServiceCompat.startForeground(this, TrackingNotifications.STEP_ID, notification, 0)

        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (detector == null || accelerometer == null) {
            state.edit().putBoolean("steps_enabled", false).putBoolean("steps_running", false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        today = LocalDate.now().toString()
        scope.launch {
            loggedToday = CounterDatabase.get(this@StepTrackingService).dao().countNow(categoryId, today)
            sensorManager.registerListener(this@StepTrackingService, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this@StepTrackingService, detector, SensorManager.SENSOR_DELAY_GAME)
        }
        sendState(true)
        return START_NOT_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            motionValidator.addAcceleration(event.timestamp, event.values[0], event.values[1], event.values[2])
            return
        }
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        val accepted = motionValidator.acceptStepDetector(event.timestamp)
        if (accepted <= 0) return
        val dateNow = LocalDate.now().toString()
        if (dateNow != today) {
            today = dateNow
            loggedToday = 0
        }
        loggedToday += accepted
        scope.launch {
            CounterDatabase.get(this@StepTrackingService).dao().addLog(
                CounterLog(date = dateNow, timestamp = System.currentTimeMillis(), categoryId = categoryId,
                    count = accepted, note = "VALIDATED_STEP_DETECTOR")
            )
            sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
                .putExtra(EXTRA_TRACKING_KIND, "steps").putExtra("delta", accepted))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("steps_running", false).putBoolean("steps_enabled", false).apply()
        sendState(false)
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun sendState(active: Boolean) = sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
        .putExtra(EXTRA_TRACKING_KIND, "steps").putExtra("active", active))

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        fun start(context: Context, categoryId: Long) = ContextCompat.startForegroundService(
            context, Intent(context, StepTrackingService::class.java).putExtra(EXTRA_CATEGORY_ID, categoryId))
        fun stop(context: Context) { context.stopService(Intent(context, StepTrackingService::class.java)) }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecognition(); stopSelf(); return START_NOT_STICKY }
        stopRequested = false
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
            model = Model(modelDir.absolutePath)
            val grammarPhrases = linkedSetOf(mantra)
            if (language == "hi") {
                grammarPhrases.add(mantra.replace("ॐ", "ओम").replace("नमः", "नम"))
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
            sendState(true, "Listening offline")
            val buffer = ByteArray(maxOf(minBuffer, 8192))
            var lastPartial = ""
            var lastCountedText = ""
            var lastCountedAt = 0L
            while (recording && !Thread.currentThread().isInterrupted) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("Microphone read failed: " + read)
                if (read > 0) {
                    val activeRecognizer = recognizer ?: continue
                    if (activeRecognizer.acceptWaveForm(buffer, read)) {
                        val transcript = JSONObject(activeRecognizer.result ?: "{}").optString("text").trim()
                        val matches = countPhrase(transcript, mantra)
                        val now = System.currentTimeMillis()
                        sendState(true, if (matches > 0) "Selected mantra matched" else "Listening offline", transcript, matches > 0)
                        val duplicate = transcript.isNotBlank() && transcript == lastCountedText && now - lastCountedAt < 1500L
                        if (matches > 0 && !duplicate) {
                            lastCountedText = transcript
                            lastCountedAt = now
                            scope.launch {
                                CounterDatabase.get(this@ChantRecognitionService).dao().addLog(
                                    CounterLog(date = LocalDate.now().toString(), timestamp = now,
                                        categoryId = categoryId, count = matches, note = "VOSK:" + language + ":" + mantra)
                                )
                                sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
                                    .putExtra(EXTRA_TRACKING_KIND, "chant").putExtra("delta", matches)
                                    .putExtra("transcript", transcript).putExtra("matched", true))
                            }
                        }
                        lastPartial = ""
                    } else {
                        val partial = JSONObject(activeRecognizer.partialResult ?: "{}").optString("partial").trim()
                        if (partial != lastPartial) {
                            lastPartial = partial
                            sendState(true, "Listening offline", partial, countPhrase(partial, mantra) > 0)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("CounterAppVosk", "Offline chant recognition stopped", error)
            sendState(false, error.message ?: "Speech engine error")
            stopSelf()
        } finally {
            recording = false
            try { audioRecord?.stop() } catch (_: Exception) { }
            audioRecord?.release(); audioRecord = null
            recognizer?.close(); recognizer = null
            model?.close(); model = null
        }
    }

    private fun countPhrase(transcript: String, phrase: String): Int {
        if (transcript.isBlank() || phrase.isBlank()) return 0
        val normalize = { value: String ->
            value.lowercase(Locale.ROOT).replace("ॐ", "ओम").replace("aum", "om")
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ").trim()
        }
        val text = normalize(transcript)
        val target = normalize(phrase)
        if (target.isBlank()) return 0
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(target) + "(?![\\p{L}\\p{N}])")
            .findAll(text).count()
    }

    private fun downloadAndExtractModel(modelUrl: String, modelName: String, target: File) {
        val zipFile = File(cacheDir, modelName + ".zip")
        val connection = URL(modelUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000; connection.readTimeout = 60000; connection.instanceFollowRedirects = true
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Model download failed (" + connection.responseCode + ")")
            connection.inputStream.use { input -> FileOutputStream(zipFile).use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
        val staging = File(filesDir, modelName + "_extract").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val safeName = entry.name.replace('\\', '/')
                if (safeName.startsWith("/") || safeName.split('/').any { it == ".." }) throw SecurityException("Invalid model archive path")
                val output = File(staging, safeName)
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { zip.copyTo(it) }
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        val extractedRoot = File(staging, modelName)
        if (!File(extractedRoot, "am/final.mdl").exists()) throw IllegalStateException("Downloaded model is incomplete")
        target.deleteRecursively()
        if (!extractedRoot.renameTo(target)) throw IllegalStateException("Could not install offline model")
        staging.deleteRecursively(); zipFile.delete()
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
        stopRecognition(); scope.cancel(); super.onDestroy()
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
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
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
