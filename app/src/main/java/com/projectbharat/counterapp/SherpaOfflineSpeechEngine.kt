package com.projectbharat.counterapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Local microphone capture with silence-based utterance segmentation and offline decoding. */
class SherpaOfflineSpeechEngine(
    private val modelFiles: OfflineSpeechModelManager.ModelFiles,
    private val english: Boolean,
    private val onTranscript: (String) -> Unit,
    private val onCount: (Int) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    fun start() {
        if (running.compareAndSet(false, true)) {
            worker = Thread({ captureAndDecode() }, "BilanOfflineVoice").apply { start() }
        }
    }

    fun stop() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        try { worker?.join(900) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        worker = null
    }

    private fun captureAndDecode() {
        var recognizer: OfflineRecognizer? = null
        var audio: AudioRecord? = null
        try {
            postStatus(if (english) "Loading offline English model…" else "मराठी ऑफलाइन मॉडेल लोड होत आहे…")
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = modelFiles.model.absolutePath),
                    tokens = modelFiles.tokens.absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "nemo"
                )
            )
            recognizer = OfflineRecognizer(config = config)

            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Microphone audio format is not supported" }
            audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 4)
            )
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize microphone" }
            recorder = audio
            audio.startRecording()
            postStatus(if (english) "Listening offline — say a number from one to ten" else "ऑफलाइन ऐकत आहे — एक ते दहा म्हणा")

            val pcm = ShortArray(FRAME_SAMPLES)
            val phraseSamples = ArrayList<Float>(SAMPLE_RATE * 5)
            var speaking = false
            var silence = 0
            var phraseLength = 0
            val silenceLimit = (SAMPLE_RATE * 0.65f).toInt()
            val maximumPhrase = SAMPLE_RATE * 6
            val minimumPhrase = SAMPLE_RATE / 4

            while (running.get()) {
                val read = audio.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                var energy = 0.0
                val frame = FloatArray(read)
                for (i in 0 until read) {
                    val value = pcm[i] / 32768.0f
                    frame[i] = value
                    energy += value * value
                }
                val hasVoice = sqrt(energy / read).toFloat() >= 0.010f
                if (hasVoice) {
                    speaking = true
                    silence = 0
                    phraseLength += read
                    frame.forEach { phraseSamples.add(it) }
                } else if (speaking) {
                    silence += read
                    phraseLength += read
                    frame.forEach { phraseSamples.add(it) }
                }

                if (speaking && (silence >= silenceLimit || phraseLength >= maximumPhrase)) {
                    if (phraseLength >= minimumPhrase && running.get()) decodePhrase(recognizer, phraseSamples)
                    phraseSamples.clear()
                    speaking = false
                    silence = 0
                    phraseLength = 0
                    if (running.get()) postStatus(if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…")
                }
            }
        } catch (error: Throwable) {
            if (running.get()) postStatus("Offline voice error: " + (error.message ?: "could not start"))
        } finally {
            running.set(false)
            try { audio?.stop() } catch (_: Exception) {}
            try { audio?.release() } catch (_: Exception) {}
            recorder = null
            try { recognizer?.release() } catch (_: Exception) {}
        }
    }

    private fun decodePhrase(recognizer: OfflineRecognizer, samples: List<Float>) {
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples.toFloatArray(), SAMPLE_RATE)
                recognizer.decode(stream)
                val phrase = recognizer.getResult(stream).text.trim()
                if (phrase.isNotBlank()) {
                    main.post { if (running.get()) onTranscript(phrase) }
                    val count = extractCount(phrase, english)
                    if (count > 0) main.post { if (running.get()) onCount(count) }
                }
            } finally {
                stream.release()
            }
        } catch (_: Throwable) {
            postStatus(if (english) "Could not decode that phrase. Keep speaking clearly." else "हा शब्दसमूह ओळखता आला नाही. स्पष्ट बोला.")
        }
    }

    private fun postStatus(value: String) {
        main.post { if (running.get()) onStatus(value) }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 320

        fun extractCount(phrase: String, english: Boolean): Int {
            val words = phrase.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
            val numberMap = if (english) mapOf(
                "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
                "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
            ) else mapOf(
                "एक" to 1, "एका" to 1, "दोन" to 2, "तीन" to 3, "चार" to 4,
                "पाच" to 5, "पांच" to 5, "सहा" to 6, "सात" to 7, "आठ" to 8,
                "नऊ" to 9, "दहा" to 10, "ek" to 1, "don" to 2, "teen" to 3,
                "tin" to 3, "char" to 4, "chaar" to 4, "pach" to 5, "paach" to 5,
                "saha" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "daha" to 10
            )
            return words.sumOf { token ->
                numberMap[token] ?: token.mapNotNull { ch ->
                    Character.digit(ch, 10).takeIf { it >= 0 }
                }.joinToString("").toIntOrNull()?.takeIf { it in 1..10 } ?: 0
            }
        }
    }
}
