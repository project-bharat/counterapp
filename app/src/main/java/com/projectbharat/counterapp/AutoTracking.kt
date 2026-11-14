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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
    val available = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
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
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startService() else message = if (english) "Notification permission is required" else "सूचना परवानगी आवश्यक आहे"
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startService()
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
                    else -> Text(if (running) if (english) "Motion tracking active" else "हालचाल मोजणी सुरू आहे" else if (english) "Motion-validated steps are counted" else "हालचाल पडताळलेली पावले मोजली जातात", color = ink.copy(alpha = .65f), textAlign = TextAlign.Center)
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
                    modifier = Modifier.fillMaxWidth(.82f)
                ) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Text(if (running) if (english) "Stop tracking" else "मोजणी थांबवा" else if (english) "Start step tracking" else "पावले मोजणे सुरू करा")
                }
                if (message.isNotBlank()) Text(message, color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
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
fun AutoChantCounterScreen(categoryId: Long, count: Int, english: Boolean, ink: Color, onMantraChange: (String) -> Unit, onManualCount: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE) }
    var target by remember(categoryId) { mutableStateOf(prefs.getString("target_$categoryId", if (english) "Ram" else "राम") ?: "राम") }
    var language by remember(categoryId) { mutableStateOf(prefs.getString("language_$categoryId", if (english) "en" else "hi") ?: "hi") }
    var automatic by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var matched by remember { mutableStateOf(false) }
    var matchedCount by remember { mutableIntStateOf(0) }
    var presetMenu by remember { mutableStateOf(false) }
    var addMantraDialog by remember { mutableStateOf(false) }
    var newMantra by remember { mutableStateOf("") }
    var customMantras by remember(categoryId) { mutableStateOf(prefs.getStringSet("custom_mantras_$categoryId", emptySet()).orEmpty().toList()) }

    val stopRecognition = {
        ChantRecognitionService.stop(context)
        running = false
        automatic = false
        status = if (english) "Stopped" else "थांबवले"
    }
    val startService = {
        if (target.isNotBlank()) {
            prefs.edit().putString("target_$categoryId", target.trim()).putString("language_$categoryId", language).apply()
            try {
                ChantRecognitionService.start(context, categoryId, target.trim(), language)
                running = true
                status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
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
                if (intent.hasExtra("delta")) { matched = true; matchedCount = intent.getIntExtra("delta", 0) }
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver); ChantRecognitionService.stop(context) }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "TODAY" else "आज", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (english) "Chants" else "जप", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(start = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "Auto count" else "स्वयंचलित मोजणी", color = ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = automatic, onCheckedChange = { enabled ->
                    if (!enabled) stopRecognition()
                    else {
                        automatic = true; matched = false; matchedCount = 0; transcript = ""
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startService()
                    }
                })
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val beadCount = if (count > 0 && count % 108 == 0) 108 else count % 108
                val beadTint by animateColorAsState(if (count > 0 && count % 108 == 0) Color(0xFFB87928) else Color(0xFF7650A1), label = "beadCountTint")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.weight(1.15f), shape = RoundedCornerShape(18.dp), color = Color(0xFFF4EFF9)) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(beadCount.toString(), color = beadTint, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                                Text(" / 108", color = beadTint.copy(alpha = .7f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(if (english) "CURRENT MALA" else "सध्याची माळ", color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(Modifier.weight(.85f), shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF4E4)) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((count / 108).toString(), color = Color(0xFFB87928), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                            Text(if (english) "MALAS DONE" else "पूर्ण माळा", color = Color(0xFF9A6828), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(target, color = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFF38234F) else ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp))
                val manualInteraction = remember { MutableInteractionSource() }
                val manualPressed by manualInteraction.collectIsPressedAsState()
                val manualScale by animateFloatAsState(if (manualPressed) 0.94f else 1f, label = "japaTapScale")
                val circleAccent by animateColorAsState(if (count > 0 && count % 108 == 0) Color(0xFFD5A24B) else Color(0xFFB895D7), label = "japaCircleAccent")
                Box(
                    Modifier.padding(top = 16.dp).size(218.dp)
                        .graphicsLayer(scaleX = manualScale, scaleY = manualScale)
                        .shadow(12.dp, CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFEFE4FA), Color(0xFFD2B9EA))), CircleShape)
                        .border(3.dp, circleAccent, CircleShape)
                        .clickable(interactionSource = manualInteraction, indication = null, onClick = { onManualCount(target) }),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SelfImprovement, null, tint = Color(0xFF7855A6), modifier = Modifier.size(42.dp))
                        Text("+1", color = ink, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                        Text(if (english) "TAP TO COUNT" else "स्पर्श करून मोजा", color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)) {
                        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = {
                                if (!running) {
                                    language = "hi"
                                    chantPresets.firstOrNull { it.devanagari == target || it.latin == target }?.let { target = it.devanagari; onMantraChange(target) }
                                }
                            }, enabled = !running, shape = RoundedCornerShape(12.dp), color = if (language == "hi") MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (language == "hi") MaterialTheme.colorScheme.onPrimary else ink) {
                                Text("अ", modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp), fontWeight = FontWeight.Bold)
                            }
                            Surface(onClick = {
                                if (!running) {
                                    language = "en"
                                    chantPresets.firstOrNull { it.devanagari == target || it.latin == target }?.let { target = it.latin; onMantraChange(target) }
                                }
                            }, enabled = !running, shape = RoundedCornerShape(12.dp), color = if (language == "en") MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (language == "en") MaterialTheme.colorScheme.onPrimary else ink) {
                                Text("A", modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { presetMenu = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(if (english) "Choose mantra" else "मंत्र निवडा")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            (chantPresets.map { if (language == "hi") it.devanagari else it.latin } + customMantras).distinct().forEach { mantra ->
                                DropdownMenuItem(text = { Text(mantra, maxLines = 2) }, onClick = {
                                    target = mantra
                                    prefs.edit().putString("target_$categoryId", target).apply()
                                    onMantraChange(target)
                                    presetMenu = false
                                })
                            }
                            DropdownMenuItem(text = { Text(if (english) "+ Add mantra" else "+ नवीन मंत्र जोडा") }, onClick = {
                                presetMenu = false
                                newMantra = ""
                                addMantraDialog = true
                            })
                        }
                    }
                }
                if (automatic) {
                    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFF3EFF8)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख", color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            if (status.contains("Preparing", true) || status.contains("तयार होत", true) || status.contains("Downloading", true) || status.contains("डाउनलोड", true)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…", color = ink, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Text(transcript.ifBlank { status.ifBlank { if (running) (if (english) "Listening…" else "ऐकत आहे…") else (if (english) "Starting…" else "सुरू होत आहे…") } },
                                    color = if (matched) Color(0xFF16834F) else ink, fontWeight = if (matched) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium)
                                if (matched) Text(
                                    (if (english) "Mantra matched" else "मंत्र जुळला") + if (matchedCount > 0) " · +$matchedCount" else "",
                                    color = Color(0xFF16834F), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (automatic && !running && status.isNotBlank() && status != "Stopped" && status != "थांबवले") {
                            TextButton(onClick = startService) { Text(if (english) "Resume" else "पुन्हा सुरू करा") }
                        }
                        Surface(onClick = stopRecognition, shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE7E7)) {
                            Text(if (english) "Stop" else "थांबवा", color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
    if (addMantraDialog) {
        AlertDialog(
            onDismissRequest = { addMantraDialog = false },
            title = { Text(if (english) "Add mantra" else "नवीन मंत्र") },
            text = { OutlinedTextField(value = newMantra, onValueChange = { newMantra = it }, label = { Text(if (english) "Mantra text" else "मंत्र") }) },
            confirmButton = {
                TextButton(onClick = {
                    val value = newMantra.trim()
                    if (value.isNotBlank()) {
                        customMantras = (customMantras + value).distinct()
                        prefs.edit().putStringSet("custom_mantras_$categoryId", customMantras.toSet())
                            .putString("target_$categoryId", value).apply()
                        target = value
                        onMantraChange(value)
                    }
                    addMantraDialog = false
                }, enabled = newMantra.isNotBlank()) { Text(if (english) "Add" else "जोडा") }
            },
            dismissButton = { TextButton(onClick = { addMantraDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(if (english) "Auto detect" else "स्वयंचलित ओळख", color = ink, fontWeight = FontWeight.SemiBold)
                    }
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = linear != null)
                }
                if (linear == null) {
                    Text(if (english) "This phone does not expose a linear-acceleration sensor." else "या फोनमध्ये linear-acceleration सेन्सर उपलब्ध नाही.", color = Color(0xFFB45353), style = MaterialTheme.typography.labelSmall)
                }
                if (enabled && linear != null) {
                    Text(status.ifBlank { if (calibrated) "Ready" else if (english) "Calibrating…" else "कॅलिब्रेट होत आहे…" },
                        color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


@Composable
fun BabyMotionSensorEngine(
    activeSessionId: Long,
    enabled: Boolean,
    onDetected: (Long, Float) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val linear = remember { manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val gyro = remember { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val detectedCallback by rememberUpdatedState(onDetected)
    val statusCallback by rememberUpdatedState(onStatus)

    DisposableEffect(enabled, activeSessionId, linear, gyro, lifecycleOwner) {
        if (enabled && linear != null) {
            val startedAt = System.currentTimeMillis()
            val samples = ArrayList<Float>()
            var baseline = 0f
            var threshold = 0.55f
            var gyroMagnitude = 0f
            var lastDetection = 0L
            var calibrated = false
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
                    if (!calibrated) {
                        samples.add(magnitude)
                        if (System.currentTimeMillis() - startedAt >= 8000L && samples.size >= 40) {
                            val sorted = samples.sorted()
                            baseline = sorted[sorted.size / 2]
                            val deviations = sorted.map { kotlin.math.abs(it - baseline) }.sorted()
                            threshold = maxOf(0.55f, baseline + 5f * deviations[deviations.size / 2])
                            calibrated = true
                            statusCallback(if (true) "Ready" else "Ready")
                        }
                        return
                    }
                    val spike = magnitude > maxOf(threshold, baseline + 0.65f)
                    val quietRotation = gyro == null || gyroMagnitude < 1.2f
                    val now = System.currentTimeMillis()
                    if (spike && quietRotation && now - lastDetection > 2200L) {
                        lastDetection = now
                        detectedCallback(now, magnitude)
                        statusCallback("Saved")
                    } else if (!spike) baseline = baseline * 0.995f + magnitude * 0.005f
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            var registered = false
            val register = {
                if (!registered) {
                    manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
                    gyro?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                    registered = true
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> register()
                    Lifecycle.Event.ON_STOP -> { manager.unregisterListener(listener); registered = false }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
            statusCallback(if (linear == null) "Sensor unavailable" else "Calibrating")
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                manager.unregisterListener(listener)
            }
        } else {
            onDispose { }
        }
    }
}

