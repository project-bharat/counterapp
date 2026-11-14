package com.projectbharat.counterapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlin.math.sqrt

private fun registerTrackingReceiver(context: Context, receiver: BroadcastReceiver) {
    val filter = IntentFilter(TRACKING_STATE_CHANGED)
    if (Build.VERSION.SDK_INT >= 33) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}

@Composable
fun AutoStepCounterScreen(categoryId: Long, count: Int, english: Boolean, ink: Color) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val available = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    var running by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_running", false)) }
    var message by remember { mutableStateOf("") }

    val startService = {
        try {
            StepTrackingService.start(context, categoryId)
            running = true
            message = if (english) "Tracking active" else "मोजणी सुरू"
        } catch (error: Exception) {
            message = error.message ?: if (english) "Could not start tracking" else "मोजणी सुरू झाली नाही"
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startService()
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startService()
        } else message = if (english) "Activity permission is required" else "हालचाल परवानगी आवश्यक आहे"
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) == "steps" && intent.hasExtra("active")) {
                    running = intent.getBooleanExtra("active", false)
                }
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver); StepTrackingService.stop(context) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            running = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_running", running)
            delay(1500)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "STEPS TODAY" else "आजची पावले", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Surface(shape = CircleShape, color = if (running) Color(0xFFE1F3E9) else Color(0xFFF1EAF7)) {
                Text(if (running) if (english) "● AUTO ON" else "● सुरू" else if (english) "PAUSED" else "थांबले",
                    color = if (running) Color(0xFF16764E) else Color(0xFF76528D),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color(0xFFE5F0FA), modifier = Modifier.size(76.dp)) {
                    Icon(Icons.Default.DirectionsWalk, null, tint = Color(0xFF3977A8), modifier = Modifier.padding(18.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (english) "steps today" else "आजची पावले", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                when {
                    !available -> Text(if (english) "This phone has no step-counter sensor" else "या फोनमध्ये स्टेप काउंटर सेन्सर नाही", color = Color(0xFFB45353), textAlign = TextAlign.Center)
                    !permissionGranted -> Text(if (english) "Allow physical activity access to count automatically" else "पावले आपोआप मोजण्यासाठी हालचाल परवानगी द्या", color = ink.copy(alpha = .65f), textAlign = TextAlign.Center)
                    else -> Text(if (running) if (english) "Counting with the built-in sensor" else "फोनच्या सेन्सरने मोजणी सुरू आहे" else if (english) "Start to count without tapping" else "टॅप न करता मोजण्यासाठी सुरू करा", color = ink.copy(alpha = .65f), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = available,
                    onClick = {
                        if (running) { StepTrackingService.stop(context); running = false }
                        else if (Build.VERSION.SDK_INT >= 29 && !permissionGranted) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startService()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Text(if (running) if (english) "Stop tracking" else "मोजणी थांबवा" else if (english) "Start step tracking" else "पावले मोजणे सुरू करा")
                }
                if (message.isNotBlank()) Text(message, color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Text(if (english) "Uses TYPE_STEP_COUNTER and STEP_DETECTOR. Tracking continues with a notification." else "TYPE_STEP_COUNTER सेन्सर वापरतो. सूचना दिसत असताना मोजणी सुरू राहते.", color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

private data class ChantPreset(val title: String, val devanagari: String, val latin: String)

private val chantPresets = listOf(
    ChantPreset("Om Namah Shivaya", "ॐ नमः शिवाय", "Om Namah Shivaya"),
    ChantPreset("Maha Mrityunjaya Mantra", "ॐ त्र्यम्बकं यजामहे सुगन्धिं पुष्टिवर्धनम्। उर्वारुकमिव बन्धनान्मृत्योर्मुक्षीय मामृतात्॥", "Om Tryambakam Yajamahe Sugandhim Pushtivardhanam Urvarukamiva Bandhanan Mrityor Mukshiya Maamritat"),
    ChantPreset("Om Namo Bhagavate Vasudevaya", "ॐ नमो भगवते वासुदेवाय", "Om Namo Bhagavate Vasudevaya"),
    ChantPreset("Hare Krishna Mahamantra", "हरे कृष्ण हरे कृष्ण कृष्ण कृष्ण हरे हरे", "Hare Krishna Hare Krishna Krishna Krishna Hare Hare"),
    ChantPreset("Om Gam Ganapataye Namaha", "ॐ गं गणपतये नमः", "Om Gam Ganapataye Namaha"),
    ChantPreset("Om Dum Durgaye Namaha", "ॐ दुं दुर्गायै नमः", "Om Dum Durgaye Namaha"),
    ChantPreset("Gayatri Mantra", "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्॥", "Om Bhur Bhuvah Svah Tat Savitur Varenyam Bhargo Devasya Dhimahi Dhiyo Yo Nah Prachodayat"),
    ChantPreset("Shri Ram Jai Ram", "श्री राम जय राम जय जय राम", "Shri Ram Jai Ram Jai Jai Ram"),
    ChantPreset("Om Hanumate Namah", "ॐ हनुमते नमः", "Om Hanumate Namah"),
    ChantPreset("Guru Mantra", "गुरुर्ब्रह्मा गुरुर्विष्णुः गुरुर्देवो महेश्वरः। गुरुः साक्षात् परब्रह्म तस्मै श्रीगुरवे नमः॥", "Gurur Brahma Gurur Vishnuh Gurur Devo Maheshwarah Guruh Sakshat Parabrahma Tasmai Shri Gurave Namah"),
    ChantPreset("Om (Pranava)", "ॐ", "Om"),
    ChantPreset("Lokah Samastah Sukhino Bhavantu", "लोकाः समस्ताः सुखिनो भवन्तु", "Lokah Samastah Sukhino Bhavantu")
)

@Composable
fun AutoChantCounterScreen(categoryId: Long, count: Int, english: Boolean, ink: Color, onManualCount: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE) }
    var target by remember(categoryId) {
        mutableStateOf(prefs.getString("target_$categoryId", if (english) "Ram" else "राम") ?: "राम")
    }
    var language by remember(categoryId) {
        mutableStateOf(prefs.getString("language_$categoryId", if (english) "en" else "hi") ?: "hi")
    }
    var automatic by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var matched by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }

    val stopRecognition = {
        ChantRecognitionService.stop(context)
        running = false
        automatic = false
        status = if (english) "Voice counting stopped" else "आवाजाद्वारे मोजणी थांबवली"
    }
    val startService = {
        if (target.isNotBlank()) {
            prefs.edit().putString("target_$categoryId", target.trim())
                .putString("language_$categoryId", language).apply()
            try {
                ChantRecognitionService.start(context, categoryId, target.trim(), language)
                running = true
                status = if (english) "Preparing offline speech model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
            } catch (error: Exception) {
                running = false
                automatic = false
                status = error.message ?: if (english) "Could not start" else "सुरू झाले नाही"
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startService() else { automatic = false; status = if (english) "Notification permission denied" else "सूचना परवानगी नाकारली" }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startService()
        } else { automatic = false; status = if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली" }
    }

    DisposableEffect(context, categoryId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) != "chant") return
                if (intent.hasExtra("active")) running = intent.getBooleanExtra("active", false)
                intent.getStringExtra("message")?.let { status = it }
                intent.getStringExtra("transcript")?.let { transcript = it }
                if (intent.hasExtra("matched")) matched = intent.getBooleanExtra("matched", false)
                if (intent.hasExtra("delta")) matched = true
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose {
            context.unregisterReceiver(receiver)
            ChantRecognitionService.stop(context)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "TODAY" else "आजचा जप", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("${count / 108} ${if (english) "mala" else "माळ"} · ${count % 108}/108", color = Color(0xFFB17B28), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "Auto" else "ऑटो", color = ink, style = MaterialTheme.typography.labelMedium)
                Switch(checked = automatic, onCheckedChange = { checked ->
                    if (!checked) stopRecognition()
                    else {
                        automatic = true
                        matched = false
                        transcript = ""
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startService()
                    }
                })
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (english) "CHANTS" else "जप", color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onManualCount, modifier = Modifier.fillMaxWidth().height(104.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+1", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                        Text(if (english) "TAP TO COUNT" else "मोजण्यासाठी स्पर्श करा", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (english) "Manual counting" else "मॅन्युअल मोजणी",
                    color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (automatic || running) {
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Box {
                        OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (english) "Choose mantra" else "मंत्र निवडा")
                        }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            chantPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(if (english) preset.title else preset.devanagari, maxLines = 2) },
                                    onClick = {
                                        target = if (language == "hi") preset.devanagari else preset.latin
                                        prefs.edit().putString("target_$categoryId", target).apply()
                                        presetMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = target, onValueChange = { target = it }, enabled = !running,
                        singleLine = true, label = { Text(if (english) "Mantra to recognise" else "ओळखायचा मंत्र") },
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = language == "hi", onClick = {
                            language = "hi"
                            chantPresets.firstOrNull { it.devanagari == target || it.latin == target }?.let { target = it.devanagari }
                        }, label = { Text(if (english) "Devanagari" else "देवनागरी") }, enabled = !running)
                        FilterChip(selected = language == "en", onClick = {
                            language = "en"
                            chantPresets.firstOrNull { it.devanagari == target || it.latin == target }?.let { target = it.latin }
                        }, label = { Text("English") }, enabled = !running)
                    }
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFF3EFF8)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख",
                                color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(transcript.ifBlank {
                                if (running) (if (english) "Listening…" else "ऐकत आहे…")
                                else status.ifBlank { if (english) "Voice count is off" else "आवाज मोजणी बंद" }
                            }, color = if (matched) Color(0xFF16834F) else ink,
                                fontWeight = if (matched) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                            if (matched) Text(if (english) "Matched" else "मंत्र जुळला", color = Color(0xFF16834F), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExperimentalBabyMotionControl(activeSessionId: Long?, english: Boolean, ink: Color, onDetected: (Long, Float) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val linear = remember { manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val gyro = remember { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var enabled by remember(activeSessionId) { mutableStateOf(false) }
    var calibrated by remember { mutableStateOf(false) }
    var detections by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(enabled, activeSessionId, linear, gyro, lifecycleOwner) {
        if (enabled && activeSessionId != null && linear != null) {
            val startedAt = System.currentTimeMillis()
            val samples = ArrayList<Float>()
            var baseline = 0f
            var threshold = 0.55f
            var gyroMagnitude = 0f
            var lastDetection = 0L
            var calibratedLocal = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        val v = event.values
                        gyroMagnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                        return
                    }
                    if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
                    val v = event.values
                    val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    if (!calibratedLocal) {
                        samples.add(magnitude)
                        if (System.currentTimeMillis() - startedAt >= 8000L && samples.size >= 40) {
                            val sorted = samples.sorted()
                            baseline = sorted[sorted.size / 2]
                            val deviations = sorted.map { kotlin.math.abs(it - baseline) }.sorted()
                            val mad = deviations[deviations.size / 2]
                            threshold = maxOf(0.55f, baseline + 5f * mad)
                            calibratedLocal = true
                            calibrated = true
                            status = if (english) "Baseline set · experimental only" else "मूलभूत कंपन नोंदले · प्रायोगिक"
                        }
                        return
                    }
                    val spike = magnitude > maxOf(threshold, baseline + 0.65f)
                    val quietRotation = gyro == null || gyroMagnitude < 1.2f
                    val now = System.currentTimeMillis()
                    if (spike && quietRotation && now - lastDetection > 2200L) {
                        lastDetection = now
                        detections++
                        onDetected(now, magnitude)
                        status = if (english) "Moment saved for review" else "संकेत तपासणीसाठी जतन केला"
                    } else if (!spike) {
                        baseline = baseline * 0.995f + magnitude * 0.005f
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            var registered = false
            val registerSensors = {
                if (!registered) {
                    manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
                    gyro?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                    registered = true
                }
            }
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> registerSensors()
                    Lifecycle.Event.ON_STOP -> { manager.unregisterListener(listener); registered = false }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) registerSensors()
            calibrated = false
            status = if (english) "Keep phone still for 8-second baseline…" else "८ सेकंद फोन स्थिर ठेवा…"
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                manager.unregisterListener(listener)
            }
        } else {
            calibrated = false
            onDispose { }
        }
    }

    if (activeSessionId != null) {
        Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (english) "Auto detect" else "स्वयंचलित ओळख", color = ink, fontWeight = FontWeight.SemiBold)
                    }
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = linear != null)
                }
                if (linear == null) {
                    Text(if (english) "This phone does not expose a linear-acceleration sensor." else "या फोनमध्ये linear-acceleration सेन्सर उपलब्ध नाही.", color = Color(0xFFB45353), style = MaterialTheme.typography.labelSmall)
                }
                if (enabled && linear != null) {
                    Text((if (english) "Detected: " else "ओळखले: ") + detections, color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(status.ifBlank { if (calibrated) "Baseline ready" else "Calibrating…" }, color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                    Text(if (english) "A phone accelerometer cannot reliably separate fetal movement from breathing, speech or maternal motion. Manual logging remains the primary record; this is not a medical monitor." else "फोनचा सेन्सर बाळाची हालचाल आणि श्वास, बोलणे किंवा आईची हालचाल विश्वासार्हपणे वेगळी ओळखू शकत नाही. मॅन्युअल नोंद मुख्य आहे; हे वैद्यकीय उपकरण नाही.", color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
