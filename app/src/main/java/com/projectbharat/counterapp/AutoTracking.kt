package com.projectbharat.counterapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale
import java.util.regex.Pattern

@Composable
fun AutoStepCounterScreen(
    categoryId: Long,
    count: Int,
    english: Boolean,
    ink: Color,
    onDelta: (Int) -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    var sensorTotal by remember { mutableIntStateOf(count) }
    var sensorStatus by remember { mutableStateOf("WAITING") }
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val dateNow = LocalDate.now().toString()
            if (dateNow != today) today = dateNow
        }
    }
    val latestCount by rememberUpdatedState(count)
    val latestOnDelta by rememberUpdatedState(onDelta)
    var submitted by remember(categoryId, today) { mutableIntStateOf(count) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }

    DisposableEffect(stepSensor, permissionGranted, categoryId, today) {
        if (permissionGranted && stepSensor != null) {
            val prefs = context.getSharedPreferences("auto_tracking", Context.MODE_PRIVATE)
            val baselineKey = "steps_baseline_${categoryId}_${today}"
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val raw = event.values.firstOrNull()?.toInt() ?: return
                    val baseline = prefs.getInt(baselineKey, -1)
                    if (baseline < 0) {
                        prefs.edit().putInt(baselineKey, raw).apply()
                        sensorTotal = 0
                        submitted = latestCount
                        sensorStatus = "TRACKING"
                        return
                    }
                    if (raw < baseline) {
                        // The phone rebooted; the hardware counter restarted from zero.
                        prefs.edit().putInt(baselineKey, 0).apply()
                    }
                    val todaySteps = (raw - if (raw < baseline) 0 else baseline).coerceAtLeast(0)
                    sensorTotal = todaySteps
                    val missing = todaySteps - submitted
                    if (missing > 0) {
                        latestOnDelta(missing)
                        submitted += missing
                    }
                    sensorStatus = "TRACKING"
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose { }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "STEPS TODAY" else "आजची पावले", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Surface(shape = CircleShape, color = Color(0xFFE1F3E9)) {
                Text(if (english) "● AUTO" else "● ऑटो", color = Color(0xFF16764E), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color(0xFFE5F0FA), modifier = Modifier.size(76.dp)) {
                    Icon(Icons.Default.DirectionsWalk, null, tint = Color(0xFF3977A8), modifier = Modifier.padding(18.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (english) "steps recorded" else "पावले नोंदवली", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Text(if (english) "Sensor reading: $sensorTotal" else "सेन्सर मोजणी: $sensorTotal", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
                Text(
                    when {
                        stepSensor == null -> if (english) "Step counter sensor unavailable" else "या फोनमध्ये स्टेप सेन्सर उपलब्ध नाही"
                        !permissionGranted -> if (english) "Allow activity recognition to count steps automatically" else "पावले आपोआप मोजण्यासाठी हालचाल परवानगी द्या"
                        else -> if (english) "Counting automatically in the background" else "पावले आपोआप मोजली जात आहेत"
                    },
                    color = ink.copy(alpha = .6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
                if (stepSensor != null && !permissionGranted) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
                        Text(if (english) "Allow step tracking" else "परवानगी द्या")
                    }
                }
            }
        }
        Text(if (english) "Uses your phone's built-in step sensor. Keep the phone with you." else "फोनमधील अंगभूत स्टेप सेन्सर वापरतो. फोन जवळ ठेवा.", color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun AutoChantCounterScreen(
    categoryId: Long,
    count: Int,
    english: Boolean,
    ink: Color,
    onRecognized: (Int) -> Unit
) {
    val context = LocalContext.current
    var target by remember(categoryId, english) { mutableStateOf(if (english) "Ram" else "राम") }
    var listening by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var lastHeard by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("IDLE") }
    val latestTarget by rememberUpdatedState(target)
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    val latestListening by rememberUpdatedState(listening)
    val latestPermission by rememberUpdatedState(permissionGranted)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) status = "PERMISSION"
    }
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember {
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val speechIntent = remember(english) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (english) "en-IN" else "mr-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (english) "en-IN" else "mr-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    DisposableEffect(recognizer, speechIntent, categoryId) {
        if (recognizer != null) {
            fun restart() {
                if (latestListening && latestPermission) {
                    handler.postDelayed({
                        if (latestListening) {
                            try { recognizer.startListening(speechIntent); status = "LISTENING" }
                            catch (_: Exception) { status = "ERROR" }
                        }
                    }, 350L)
                }
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { status = "LISTENING" }
                override fun onBeginningOfSpeech() { status = "HEARING" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status = "PROCESSING" }
                override fun onError(error: Int) {
                    if (latestListening) {
                        status = "RETRYING"
                        restart()
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    lastHeard = heard
                    val phrase = latestTarget.trim()
                    if (phrase.isNotEmpty() && heard.isNotEmpty()) {
                        val matcher = Pattern.compile(Pattern.quote(phrase), Pattern.CASE_INSENSITIVE).matcher(heard)
                        var matches = 0
                        while (matcher.find()) matches++
                        if (matches > 0) latestOnRecognized(matches)
                    }
                    restart()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
            recognizer?.destroy()
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "CHANTS TODAY" else "आजचा जप", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Surface(shape = CircleShape, color = if (listening) Color(0xFFE1F3E9) else Color(0xFFF1EAF7)) {
                Text(if (listening) if (english) "● LISTENING" else "● ऐकत आहे" else if (english) "VOICE OFF" else "आवाज बंद",
                    color = if (listening) Color(0xFF16764E) else Color(0xFF76528D),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color(0xFFF0E6FA), modifier = Modifier.size(76.dp)) {
                    Icon(if (listening) Icons.Default.Mic else Icons.Default.MicOff, null, tint = Color(0xFF8657A7), modifier = Modifier.padding(19.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (english) "chants counted" else "जप मोजले", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = target, onValueChange = { target = it }, singleLine = true,
                    label = { Text(if (english) "Word or mantra to count" else "मोजायचा शब्द / मंत्र") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                when {
                    !available -> Text(if (english) "Speech recognition is unavailable on this device" else "या फोनवर आवाज ओळख उपलब्ध नाही", color = Color(0xFFB45353), textAlign = TextAlign.Center)
                    !permissionGranted -> Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Mic, null); Text(if (english) "Allow microphone" else "मायक्रोफोन परवानगी द्या")
                    }
                    else -> Button(
                        onClick = {
                            if (listening) {
                                listening = false
                                recognizer?.cancel()
                                status = "IDLE"
                            } else if (target.isNotBlank()) {
                                listening = true
                                try { recognizer?.startListening(speechIntent); status = "LISTENING" }
                                catch (_: Exception) { status = "ERROR"; listening = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (listening) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Text(if (listening) if (english) "Stop auto count" else "मोजणी थांबवा" else if (english) "Start auto count" else "स्वयंचलित मोजणी सुरू")
                    }
                }
                if (lastHeard.isNotBlank()) {
                    Text((if (english) "Heard: " else "ऐकले: ") + lastHeard, color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    when (status) {
                        "LISTENING", "HEARING" -> if (english) "Say the selected word clearly" else "निवडलेला शब्द स्पष्ट म्हणा"
                        "PROCESSING", "RETRYING" -> if (english) "Recognising…" else "ओळखत आहे…"
                        "ERROR" -> if (english) "Restart listening" else "पुन्हा ऐकणे सुरू करा"
                        else -> if (english) "Only the selected word is counted" else "फक्त निवडलेला शब्द मोजला जाईल"
                    },
                    color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Text(if (english) "Speech recognition may use your device's speech service and may require internet. Review the recognised words." else "आवाज ओळखण्यासाठी फोनची Speech सेवा व इंटरनेट लागू शकते. ओळखलेले शब्द तपासा.", color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}
