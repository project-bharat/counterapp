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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun BabyVoiceCountControl(
    activeSessionId: Long,
    english: Boolean,
    onCount: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listening by remember(activeSessionId) { mutableStateOf(false) }
    var status by remember(activeSessionId) { mutableStateOf("") }
    val callback = rememberUpdatedState(onCount)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listening = true
        else status = if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक"
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) listening = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(listening, activeSessionId, english) {
        if (!listening) {
            onDispose { }
        } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = if (english) "Speech recognition unavailable" else "आवाज ओळख उपलब्ध नाही"
            listening = false
            onDispose { }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            onDispose { }
        } else {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val handler = Handler(Looper.getMainLooper())
            var disposed = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (english) "en-IN" else "mr-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (english) "en-IN" else "mr-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            lateinit var restart: Runnable
            fun startAgain() {
                if (!disposed && listening) {
                    try { recognizer.startListening(intent) } catch (_: Exception) {
                        handler.postDelayed(restart, 900)
                    }
                }
            }
            restart = Runnable { startAgain() }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status = if (english) "Listening — say one, two, three…" else "ऐकत आहे — एक, दोन, तीन म्हणा…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!disposed && listening) handler.postDelayed(restart, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 650)
                }
                override fun onResults(results: Bundle?) {
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val words = phrases.firstOrNull().orEmpty().lowercase()
                        .replace(Regex("[^\\p{L}\\s]"), " ")
                        .split(Regex("\\s+"))
                    val numberWords = if (english) {
                        setOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
                    } else {
                        setOf("एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ", "दहा", "दो", "चार")
                    }
                    val count = words.count { it in numberWords }
                    if (count > 0) callback.value(count)
                    handler.postDelayed(restart, 350)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
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

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (listening) listening = false
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    status = ""
                    listening = true
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
            Text(if (listening) (if (english) "Stop voice" else "आवाज बंद") else (if (english) "Voice count" else "आवाजाने मोजा"))
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}
