package com.projectbharat.counterapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun BabyVoiceCountControl(
    activeSessionId: Long,
    english: Boolean,
    onCount: (Int) -> Unit,
    onRecognitionState: (Boolean, String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listening by remember(activeSessionId) { mutableStateOf(false) }
    val callback = rememberUpdatedState(onCount)
    val stateCallback = rememberUpdatedState(onRecognitionState)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true
        else stateCallback.value(false, if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक", "")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) listening = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(listening, activeSessionId, english) {
        if (!listening) {
            stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "")
            onDispose { }
        } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            stateCallback.value(false, if (english) "Speech recognition unavailable" else "आवाज ओळख उपलब्ध नाही", "")
            listening = false
            onDispose { }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            onDispose { }
        } else {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val handler = Handler(Looper.getMainLooper())
            var disposed = false
            var ready = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (english) "en-IN" else "mr-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (english) "en-IN" else "mr-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            lateinit var restart: Runnable
            fun startAgain() {
                if (!disposed && listening) {
                    try {
                        ready = false
                        stateCallback.value(true, if (english) "Preparing offline speech recognition…" else "ऑफलाइन आवाज ओळख तयार होत आहे…", "")
                        recognizer.startListening(intent)
                    } catch (_: Exception) { handler.postDelayed(restart, 900) }
                }
            }
            restart = Runnable { startAgain() }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    ready = true
                    stateCallback.value(true, if (english) "Listening offline — say one, two, three…" else "ऑफलाइन ऐकत आहे — एक, दोन, तीन म्हणा…", "")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!disposed && listening) {
                        if (!ready && error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                            stateCallback.value(true, if (english) "Offline language model unavailable. Check speech services." else "ऑफलाइन भाषा मॉडेल उपलब्ध नाही. Speech Services तपासा.", "")
                        }
                        handler.postDelayed(restart, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 650)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    stateCallback.value(true, if (english) "Listening offline — say one, two, three…" else "ऑफलाइन ऐकत आहे — एक, दोन, तीन म्हणा…", phrase)
                    val words = phrase.lowercase()
                        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                    val numberWords = setOf(
                        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
                        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
                        "एक" to 1, "दोन" to 2, "तीन" to 3, "चार" to 4, "पाच" to 5,
                        "सहा" to 6, "सात" to 7, "आठ" to 8, "नऊ" to 9, "दहा" to 10,
                        "एक" to 1, "दो" to 2, "दोनों" to 2, "पाँच" to 5, "छह" to 6,
                        "आठ" to 8, "नौ" to 9
                    )
                    val count = words.sumOf { token ->
                        numberWords[token] ?: token.mapNotNull { ch ->
                            Character.digit(ch, 10).takeIf { it >= 0 }
                        }.joinToString("").toIntOrNull()?.takeIf { it in 1..10 }?.let { 1 } ?: 0
                    }
                    if (count > 0) callback.value(count)
                    handler.postDelayed(restart, 350)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (phrase.isNotBlank()) stateCallback.value(true, if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख", phrase)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startAgain()
            onDispose {
                disposed = true
                handler.removeCallbacksAndMessages(null)
                try { recognizer.cancel(); recognizer.destroy() } catch (_: Exception) {}
            }
        }
    }

    Button(
        onClick = {
            if (listening) listening = false
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        modifier = Modifier.height(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(5.dp))
        Text(if (listening) (if (english) "Stop" else "बंद") else (if (english) "Voice count" else "आवाज मोजा"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun BabyVoiceRecognitionPanel(listening: Boolean, english: Boolean, status: String, transcript: String) {
    val pulse = rememberInfiniteTransition(label = "babyVoicePulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "babyVoiceMic")
    Surface(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (listening) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Default.Mic, null, modifier = Modifier.size(21.dp).graphicsLayer(scaleX = if (listening) scale else 1f, scaleY = if (listening) scale else 1f), tint = MaterialTheme.colorScheme.primary)
                Text(if (listening) (if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख") else (if (english) "VOICE RECOGNITION" else "आवाज ओळख"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            if (listening && (status.contains("Preparing", true) || status.contains("तयार होत", true))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (english) "Starting speech recognition…" else "आवाज ओळख सुरू होत आहे…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                AnimatedContent(
                    targetState = transcript.ifBlank { status.ifBlank { if (listening) (if (english) "Listening…" else "ऐकत आहे…") else (if (english) "Voice count is off" else "आवाज मोजणी बंद") } },
                    transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
                    label = "babyVoiceTranscript"
                ) { phrase ->
                    Text(phrase, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (status.contains("unavailable", true) || status.contains("उपलब्ध नाही", true)) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
