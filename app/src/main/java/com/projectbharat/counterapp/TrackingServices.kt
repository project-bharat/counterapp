package com.projectbharat.counterapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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

const val TRACKING_STATE_CHANGED = "com.projectbharat.counterapp.TRACKING_STATE_CHANGED"
const val EXTRA_TRACKING_KIND = "tracking_kind"

private object TrackingNotifications {
    const val CHANNEL_ID = "active_tracking"
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
}

/** Reuses the loaded Vosk acoustic model between Japa sessions in this app process. */
private object VoskModelCache {
    private var cachedPath: String? = null
    private var cachedModel: Model? = null

    @Synchronized
    fun isCached(directory: File): Boolean = cachedPath == directory.absolutePath && cachedModel != null

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

/** Keep the grammar-specific recognizer warm across pause/resume toggles. */
private object VoskRecognizerCache {
    private var cachedKey: String? = null
    private var cachedModelPath: String? = null
    private var cachedRecognizer: Recognizer? = null

    @Synchronized
    fun isCached(key: String): Boolean = cachedKey == key && cachedRecognizer != null

    @Synchronized
    fun clearForModelChange(path: String) {
        if (cachedModelPath != null && cachedModelPath != path) {
            cachedRecognizer?.close()
            cachedRecognizer = null
            cachedKey = null
            cachedModelPath = null
        }
    }

    @Synchronized
    fun get(model: Model, modelPath: String, key: String, grammar: String): Recognizer {
        if (cachedKey == key && cachedRecognizer != null) {
            cachedRecognizer!!.reset()
            return cachedRecognizer!!
        }
        cachedRecognizer?.close()
        val next = Recognizer(model, 16000.0f, grammar)
        cachedRecognizer = next
        cachedKey = key
        cachedModelPath = modelPath
        return next
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
        if (intent?.action == ACTION_PAUSE_CHANT) { pauseRecognition(); return START_NOT_STICKY }
        val previousWorker = worker
        if (previousWorker?.isAlive == true) {
            stopRequested = true
            recording = false
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_audio_active", false).apply()
            try { audioRecord?.stop() } catch (_: Exception) { }
            previousWorker.interrupt()
        }
        categoryId = intent?.getLongExtra("category_id", -1L) ?: -1L
        mantra = intent?.getStringExtra(EXTRA_MANTRA)?.trim().orEmpty().ifBlank { "Ram" }
        language = intent?.getStringExtra(EXTRA_LANGUAGE).orEmpty().ifBlank { "en" }
        if (categoryId <= 0 || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val notification = TrackingNotifications.show(this, TrackingNotifications.CHANT_ID, "Japa · " + mantra,
            "Voice count active · " + (if (language == "en") "Preparing offline model" else "ऑफलाइन मॉडेल तयार करत आहे"),
            null)
        try {
            if (Build.VERSION.SDK_INT >= 30) ServiceCompat.startForeground(this, TrackingNotifications.CHANT_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else ServiceCompat.startForeground(this, TrackingNotifications.CHANT_ID, notification, 0)
        } catch (error: SecurityException) {
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).putBoolean("chant_audio_active", false).apply()
            sendState(false, if (language == "en") "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक")
            stopSelf()
            return START_NOT_STICKY
        } catch (error: Exception) {
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).apply()
            sendState(false, error.message ?: "Could not start microphone service")
            stopSelf()
            return START_NOT_STICKY
        }
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", true).putBoolean("chant_audio_active", false).apply()
        scope.launch {
            val log = CounterDatabase.get(this@ChantRecognitionService).dao()
                .findMantraLog(categoryId, LocalDate.now().toString(), "MANTRA:" + mantra)
            updateJapaNotification(log?.count ?: 0, "Voice count active · Listening")
        }
        sendState(false, if (language == "en") "Preparing recognizer…" else "ओळख यंत्रणा तयार होत आहे…")
        worker = Thread {
            try {
                if (previousWorker?.isAlive == true) previousWorker.join()
                if (!Thread.currentThread().isInterrupted) {
                    stopRequested = false
                    partialCounted = 0
                    runRecognition()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.also { it.name = "CounterApp-Vosk"; it.start() }
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
            val recognizerKey = modelDir.absolutePath + "|" + language + "|" + mantra.trim()
            val warmRecognizer = VoskRecognizerCache.isCached(recognizerKey)
            sendState(true, if (language == "en") {
                if (warmRecognizer) "Reconnecting microphone…" else if (VoskModelCache.isCached(modelDir)) "Preparing speech recognizer…" else "Loading offline speech model…"
            } else {
                if (warmRecognizer) "माइक पुन्हा जोडत आहे…" else if (VoskModelCache.isCached(modelDir)) "ओळख यंत्रणा तयार होत आहे…" else "ऑफलाइन आवाज मॉडेल लोड होत आहे…"
            })
            VoskRecognizerCache.clearForModelChange(modelDir.absolutePath)
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
                if (cleanMantra.contains("दिगंबरा") || cleanMantra.contains("दिगम्बरा")) {
                    // Short phrase alternatives help the small Hindi model retain repeated opening words.
                    grammarPhrases.add("दिगंबरा")
                    grammarPhrases.add("दिगम्बरा")
                    grammarPhrases.add("दिगंबरा दिगंबरा")
                    grammarPhrases.add("दिगम्बरा दिगम्बरा")
                    grammarPhrases.add("श्रीपाद वल्लभ")
                    grammarPhrases.add("श्रीपादवल्लभ")
                    grammarPhrases.add("दिगंबरा दिगंबरा श्रीपाद वल्लभ दिगंबरा")
                    grammarPhrases.add("दिगम्बरा दिगम्बरा श्रीपाद वल्लभ दिगम्बरा")
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
            recognizer = VoskRecognizerCache.get(model!!, modelDir.absolutePath, recognizerKey, grammar)
            val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("Audio input is not supported")
            val audioManager = getSystemService(AudioManager::class.java)
            if (audioManager?.mode == AudioManager.MODE_IN_CALL || (Build.VERSION.SDK_INT >= 24 && runCatching { audioManager?.activeRecordingConfigurations?.isNotEmpty() == true }.getOrDefault(false))) {
                throw IllegalStateException("Microphone busy — another app or call is using it")
            }
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 8192))
            if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw IllegalStateException("Microphone unavailable") }
            audioRecord = record
            if (stopRequested || Thread.currentThread().isInterrupted) { record.release(); audioRecord = null; return }
            try {
                record.startRecording()
            } catch (error: Exception) {
                record.release()
                audioRecord = null
                throw IllegalStateException("Microphone busy — another app or call is using it", error)
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                audioRecord = null
                throw IllegalStateException("Microphone unavailable")
            }
            recording = true
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_audio_active", true).apply()
            sendState(true, if (language == "en") "Offline model ready · listening offline" else "ऑफलाइन मॉडेल तयार · ऐकत आहे")
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
                getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).putBoolean("chant_audio_active", false).apply()
                val detail = error.message.orEmpty()
                val friendly = when {
                    detail.contains("busy", true) || detail.contains("startRecording", true) ->
                        if (language == "en") "Microphone busy — another app or call is using it" else "मायक्रोफोन व्यस्त — दुसरे अॅप किंवा कॉल वापरत आहे"
                    detail.contains("Microphone unavailable", true) || detail.contains("audio input", true) ->
                        if (language == "en") "Microphone unavailable" else "मायक्रोफोन उपलब्ध नाही"
                    detail.contains("permission", true) || error is SecurityException ->
                        if (language == "en") "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक"
                    else -> detail.ifBlank { if (language == "en") "Voice recognition stopped" else "आवाज ओळख थांबली" }
                }
                sendState(false, friendly)
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
                stopSelf()
            }
        } finally {
            recording = false
            getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_audio_active", false).apply()
            try { audioRecord?.stop() } catch (_: Exception) { }
            audioRecord?.release(); audioRecord = null
            recognizer = null
            // Both model and grammar recognizer remain cached for a fast next start.
        }
    }

    private fun sendRecognitionUpdate(transcript: String, matches: Int) {
        val matched = matches > 0 || countPhrase(transcript, mantra) > 0
        sendState(true, if (matched) "Mantra matched" else "Listening offline", transcript, matched)
        scope.launch {
            val dao = CounterDatabase.get(this@ChantRecognitionService).dao()
            if (matches > 0) {
                dao.incrementMantraDaily(categoryId, LocalDate.now().toString(), mantra, matches)
                val log = dao.findMantraLog(categoryId, LocalDate.now().toString(), "MANTRA:" + mantra)
                updateJapaNotification(log?.count ?: 0, transcript.ifBlank { "Mantra matched" })
                sendBroadcast(Intent(TRACKING_STATE_CHANGED).setPackage(packageName)
                    .putExtra(EXTRA_TRACKING_KIND, "chant").putExtra("delta", matches)
                    .putExtra("transcript", transcript).putExtra("matched", true))
            } else {
                val log = dao.findMantraLog(categoryId, LocalDate.now().toString(), "MANTRA:" + mantra)
                updateJapaNotification(log?.count ?: 0, transcript.ifBlank { "Listening offline" })
            }
        }
    }

    private fun updateJapaNotification(total: Int, liveText: String) {
        val notification = TrackingNotifications.show(this, TrackingNotifications.CHANT_ID,
            "Japa · $total", liveText.take(120), null)
        getSystemService(NotificationManager::class.java).notify(TrackingNotifications.CHANT_ID, notification)
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

        // Vosk often drops "दिगंबरा" and returns only repeated "श्रीपाद वल्लभ".
        if (target.contains("दिगंबरा")) {
            val vallabhaCount = Regex("श्रीपाद\\s+वल्लभ").findAll(text).count()
            val digambaraCount = text.split(" ").count { it == "दिगंबरा" || it == "दिगम्बरा" }
            val recoveredCount = maxOf(vallabhaCount, (digambaraCount + 2) / 3)
            if (recoveredCount > 0) return recoveredCount
        }

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

    private fun pauseRecognition() {
        // Keep the started service and process-level acoustic model cache warm.
        // Stop only the microphone/recognizer so the next toggle can resume quickly.
        stopRequested = true
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        worker?.interrupt()
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit().putBoolean("chant_enabled", false).putBoolean("chant_audio_active", false).apply()
        getSystemService(NotificationManager::class.java).cancel(TrackingNotifications.CHANT_ID)
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        sendState(false, "Stopped")
    }

    private fun stopRecognition() {
        pauseRecognition()
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
        private const val ACTION_PAUSE_CHANT = "com.projectbharat.counterapp.action.PAUSE_CHANT"
        fun start(context: Context, categoryId: Long, mantra: String, language: String) {
            val intent = Intent(context, ChantRecognitionService::class.java)
                .putExtra("category_id", categoryId)
                .putExtra(EXTRA_MANTRA, mantra).putExtra(EXTRA_LANGUAGE, language)
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) { context.startService(Intent(context, ChantRecognitionService::class.java).setAction(ACTION_PAUSE_CHANT)) }
    }
}

