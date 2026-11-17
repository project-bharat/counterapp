package com.projectbharat.counterapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

@Composable
fun BabyVoiceCountControl(
    activeSessionId: Long,
    english: Boolean,
    onCount: (Int) -> Unit,
    onRecognitionState: (Boolean, String, String?, Int?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { OfflineSpeechModelManager(context.applicationContext) }
    val callback = rememberUpdatedState(onCount)
    val stateCallback = rememberUpdatedState(onRecognitionState)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var listening by remember(activeSessionId) { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true
        else stateCallback.value(false, if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक", "", null)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) listening = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listening, activeSessionId, english) {
        if (!listening) {
            stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            return@LaunchedEffect
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        var engine: SherpaOfflineSpeechEngine? = null
        try {
            stateCallback.value(true, if (english) "Checking offline model…" else "ऑफलाइन मॉडेल तपासत आहे…", "", null)
            val files = withContext(Dispatchers.IO) {
                manager.installedFiles(english) ?: manager.download(english) { done, total ->
                    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                    mainHandler.post {
                        stateCallback.value(true,
                            if (english) "Downloading offline model… $percent%" else "ऑफलाइन मॉडेल डाउनलोड होत आहे… $percent%",
                            "", null)
                    }
                }
            }
            stateCallback.value(true, if (english) "Loading offline model…" else "ऑफलाइन मॉडेल लोड होत आहे…", "", null)
            engine = SherpaOfflineSpeechEngine(
                modelFiles = files,
                english = english,
                onTranscript = { phrase, detectedCount ->
                    stateCallback.value(true, if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…", phrase, detectedCount)
                },
                onCount = { amount -> callback.value(amount) },
                onStatus = { status -> stateCallback.value(true, status, null, null) },
                onStopped = { error ->
                    stateCallback.value(false, error ?: if (english) "Voice count stopped" else "आवाज मोजणी थांबली", null, null)
                    listening = false
                }
            )
            withContext(Dispatchers.IO) { engine.start() }
            awaitCancellation()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            stateCallback.value(false, "Offline voice setup failed: " + (error.message ?: "try again"), null, null)
            listening = false
        } finally {
            withContext(Dispatchers.IO) { engine?.stop() }
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
fun BabyVoiceRecognitionPanel(listening: Boolean, english: Boolean, status: String, transcript: String, detectedCount: Int) {
    val pulse = rememberInfiniteTransition(label = "babyVoicePulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "babyVoiceMic")
    val loading = status.contains("Loading", true) || status.contains("Downloading", true) ||
        status.contains("तपास", true) || status.contains("डाउनलोड", true) || status.contains("लोड होत", true)
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp),
        color = if (listening) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Mic, null, modifier = Modifier.size(20.dp).graphicsLayer(scaleX = if (listening) scale else 1f, scaleY = if (listening) scale else 1f), tint = MaterialTheme.colorScheme.primary)
                    Text(if (listening) "LIVE RECOGNITION" else if (english) "VOICE RECOGNITION" else "आवाज ओळख", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(status.ifBlank { if (listening) (if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…") else (if (english) "Voice count is off" else "आवाज मोजणी बंद") }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(transcript.ifBlank { if (english) "Speak a number from one to ten" else "एक ते दहा अंक बोला" }, style = MaterialTheme.typography.bodyMedium,
                color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f) else MaterialTheme.colorScheme.onSurface, maxLines = 2)
            if (detectedCount > 0) {
                Spacer(Modifier.height(7.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Text("+$detectedCount", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
