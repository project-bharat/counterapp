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
import androidx.compose.material3.FilterChip
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
    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
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
    var running by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_enabled", false)) }
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
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            running = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_enabled", running)
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

@Composable
fun AutoChantCounterScreen(categoryId: Long, count: Int, english: Boolean, ink: Color) {
    val context = LocalContext.current
    var target by remember(categoryId) {
        mutableStateOf(context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE).getString("target_$categoryId", if (english) "Ram" else "राम") ?: "राम")
    }
    var language by remember(categoryId) {
        mutableStateOf(context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE).getString("language_$categoryId", if (english) "en" else "hi") ?: "hi")
    }
    var running by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_enabled", false)) }
    var status by remember { mutableStateOf(if (running) if (english) "Listening" else "ऐकत आहे" else "") }

    val startService = {
        if (target.isNotBlank()) {
            context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE).edit()
                .putString("target_$categoryId", target.trim()).putString("language_$categoryId", language).apply()
            try {
                ChantRecognitionService.start(context, categoryId, target.trim(), language)
                running = true
                status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
            } catch (error: Exception) {
                status = error.message ?: if (english) "Could not start" else "सुरू झाले नाही"
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startService() }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startService()
        } else status = if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली"
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) == "chant") {
                    if (intent.hasExtra("active")) running = intent.getBooleanExtra("active", false)
                    intent.getStringExtra("message")?.let { status = it }
                }
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            running = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_enabled", running)
            delay(1500)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "CHANTS TODAY" else "आजचा जप", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Surface(shape = CircleShape, color = if (running) Color(0xFFE1F3E9) else Color(0xFFF1EAF7)) {
                Text(if (running) if (english) "● LISTENING" else "● ऐकत आहे" else if (english) "VOICE OFF" else "आवाज बंद",
                    color = if (running) Color(0xFF16764E) else Color(0xFF76528D),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color(0xFFF0E6FA), modifier = Modifier.size(76.dp)) {
                    Icon(if (running) Icons.Default.Mic else Icons.Default.MicOff, null, tint = Color(0xFF8657A7), modifier = Modifier.padding(19.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (english) "chants" else "जप", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = target, onValueChange = { target = it }, enabled = !running, singleLine = true,
                    label = { Text(if (english) "Word / mantra" else "मोजायचा शब्द / मंत्र") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = language == "hi", onClick = { language = "hi"; if (!english) target = "राम" }, label = { Text(if (english) "Devanagari · Hindi model" else "देवनागरी") }, enabled = !running)
                    FilterChip(selected = language == "en", onClick = { language = "en"; if (english) target = "Ram" }, label = { Text("English India") }, enabled = !running)
                }
                Text(if (language == "hi") {
                    if (english) "Uses the Hindi offline model for Devanagari. Marathi-specific Vosk model is not currently listed." else "देवनागरीसाठी हिंदी ऑफलाइन मॉडेल. स्वतंत्र मराठी Vosk मॉडेल उपलब्ध नाही."
                } else if (english) "English (India) offline model" else "इंग्रजी (भारत) ऑफलाइन मॉडेल",
                    color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (running) { ChantRecognitionService.stop(context); running = false; status = if (english) "Stopped" else "थांबवले" }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else startService()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Text(if (running) if (english) "Stop auto count" else "मोजणी थांबवा" else if (english) "Start offline auto count" else "ऑफलाइन मोजणी सुरू करा")
                }
                if (status.isNotBlank()) Text(status, color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 9.dp))
            }
        }
        Text(if (english) "The first start downloads a speech model (about 36–42 MB). Later sessions run offline. Audio is processed in memory and is not saved." else "पहिल्यांदा ३६–४२ MB मॉडेल डाउनलोड होईल. पुढील मोजणी ऑफलाइन चालेल. आवाज साठवला जात नाही.", color = ink.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ExperimentalBabyMotionControl(activeSessionId: Long?, english: Boolean, ink: Color, onDetected: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val linear = remember { manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val gyro = remember { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var enabled by remember(activeSessionId) { mutableStateOf(false) }
    var calibrated by remember { mutableStateOf(false) }
    var detections by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    val latestOnDetected by rememberUpdatedState(onDetected)

    DisposableEffect(enabled, activeSessionId, linear, gyro) {
        if (enabled && activeSessionId != null && linear != null) {
            val startedAt = System.currentTimeMillis()
            val samples = ArrayList<Float>()
            var baseline = 0f
            var threshold = 1.1f
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
                        if (System.currentTimeMillis() - startedAt >= 3000L && samples.size >= 20) {
                            baseline = samples.average().toFloat()
                            val variance = samples.map { (it - baseline) * (it - baseline) }.average().toFloat()
                            threshold = maxOf(1.0f, baseline + 3f * sqrt(variance))
                            calibratedLocal = true
                            calibrated = true
                            status = if (english) "Calibrated · experimental" else "कॅलिब्रेशन पूर्ण · प्रायोगिक"
                        }
                        return
                    }
                    baseline = baseline * 0.985f + magnitude * 0.015f
                    val spike = magnitude > maxOf(threshold, baseline + 0.9f)
                    val quietRotation = gyro == null || gyroMagnitude < 1.8f
                    val now = System.currentTimeMillis()
                    if (spike && quietRotation && now - lastDetection > 1400L) {
                        lastDetection = now
                        detections++
                        latestOnDetected()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
            gyro?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            calibrated = false
            status = if (english) "Calibrating for 3 seconds…" else "३ सेकंद कॅलिब्रेशन…"
            onDispose { manager.unregisterListener(listener) }
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
                        Text(if (english) "Experimental motion assist" else "प्रायोगिक हालचाल ओळख", color = ink, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Phone movement may cause false detections" else "फोन हलल्यास चुकीची नोंद होऊ शकते", color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                    }
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Text((if (english) "Detected: " else "ओळखले: ") + detections, color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(status.ifBlank { if (calibrated) "Calibrated" else "Calibrating…" }, color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
