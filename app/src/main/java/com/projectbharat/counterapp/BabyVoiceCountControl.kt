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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

@Composable
fun BabyVoiceCountControl(
    autoStart: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
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
    val runGeneration = remember(activeSessionId) { AtomicLong(0L) }
    val engineMutex = remember(activeSessionId) { Mutex() }
    var listening by remember(activeSessionId) { mutableStateOf(false) }
    LaunchedEffect(activeSessionId, autoStart) {
        if (autoStart) {
            listening = true
            onAutoStartConsumed()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            stateCallback.value(true, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
            listening = true
        } else {
            stateCallback.value(false, if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक", "", null)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) listening = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listening, activeSessionId, english) {
        // Every language/start-stop transition invalidates callbacks from the previous engine.
        val runId = runGeneration.incrementAndGet()
        if (!listening) {
            stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            return@LaunchedEffect
        }

        // Publish the opening state before waiting for cleanup, model download, or
        // microphone acquisition. This makes the recognition panel appear immediately,
        // including during rapid start/stop and language changes.
        stateCallback.value(true, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        // Serialize engines: the next language cannot claim the microphone until the old
        // engine has released AudioRecord and its native recognizer.
        engineMutex.lock()
        var engine: SherpaOfflineSpeechEngine? = null
        try {
            stateCallback.value(true, if (english) "Checking offline model…" else "ऑफलाइन मॉडेल तपासत आहे…", "", 0)
            val files = withContext(Dispatchers.IO) {
                manager.installedFiles(english) ?: manager.download(english) { done, total ->
                    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                    mainHandler.post {
                        if (runGeneration.get() == runId) stateCallback.value(true,
                            if (english) "Downloading offline model… $percent%" else "ऑफलाइन मॉडेल डाउनलोड होत आहे… $percent%",
                            "", null)
                    }
                }
            }
            check(files.language == if (english) "en" else "mr") { "The downloaded speech model does not match the selected app language" }
            stateCallback.value(true, if (english) "Loading offline model…" else "ऑफलाइन मॉडेल लोड होत आहे…", "", 0)
            engine = SherpaOfflineSpeechEngine(
                modelFiles = files,
                english = english,
                onTranscript = { phrase, detectedCount ->
                    if (runGeneration.get() == runId) stateCallback.value(true, if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…", phrase, detectedCount)
                },
                onCount = { amount -> if (runGeneration.get() == runId) callback.value(amount) },
                onStatus = { status -> if (runGeneration.get() == runId) stateCallback.value(true, status, null, null) },
                onStopped = { error ->
                    if (runGeneration.get() == runId) {
                        stateCallback.value(false, error ?: if (english) "Voice count stopped" else "आवाज मोजणी थांबली", null, null)
                        listening = false
                    }
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
            // LaunchedEffect is cancelled on language changes. Cleanup must outlive that
            // cancellation so the previous AudioRecord/model is fully stopped before restart.
            withContext(NonCancellable + Dispatchers.IO) { engine?.stop() }
            engineMutex.unlock()
        }
    }

    Button(
        onClick = {
            // Invalidate any queued transcript/progress callback at the exact tap,
            // not only when Compose later restarts the effect.
            runGeneration.incrementAndGet()
            if (listening) {
                // Close the panel synchronously on an explicit stop. The next tap
                // opens it synchronously again, without waiting for LaunchedEffect.
                listening = false
                stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            } else {
                stateCallback.value(true, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    listening = true
                } else {
                    stateCallback.value(true, if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक", "", 0)
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
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
    val pulse = rememberInfiniteTransition(label = "babyVoiceLiveDot")
    val dotScale by pulse.animateFloat(
        0.78f, 1.18f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "babyVoiceDotScale"
    )
    val dotAlpha by pulse.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "babyVoiceDotAlpha"
    )
    val loading = status.contains("Loading", true) || status.contains("Downloading", true) ||
        status.contains("तपास", true) || status.contains("डाउनलोड", true) || status.contains("लोड होत", true)
    val liveGreen = Color(0xFF16A34A)

    Surface(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (listening) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (listening) {
                    Box(
                        Modifier.size(9.dp)
                            .graphicsLayer(scaleX = dotScale, scaleY = dotScale, alpha = dotAlpha)
                            .background(liveGreen, CircleShape)
                    )
                    Text(
                        if (english) "Listening" else "ऐकत आहे",
                        color = liveGreen,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        if (english) "Voice recognition off" else "आवाज बंद",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (transcript.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }
            if (detectedCount > 0) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Text(
                        "+$detectedCount",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
