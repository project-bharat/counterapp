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
import java.text.NumberFormat
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.projectbharat.counterapp.data.DayTotal
import com.projectbharat.counterapp.data.CounterLog
import com.projectbharat.counterapp.data.WalkingSession
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
fun AutoStepCounterScreen(
    categoryId: Long, count: Int, goal: Int, days: List<DayTotal>, sessions: List<WalkingSession>,
    range: String, onRangeChange: (String) -> Unit, onGoalChange: (Int) -> Unit,
    english: Boolean, ink: Color
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val available = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null || sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    var running by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_running", false)) }
    var message by remember { mutableStateOf("") }
    var selectedTab by remember(categoryId) { mutableIntStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var goalText by remember(goal) { mutableStateOf(goal.toString()) }

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
        } else message = if (english) "Physical activity permission is required" else "हालचाल परवानगी आवश्यक आहे"
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) == "steps" && intent.hasExtra("active"))
                    running = intent.getBooleanExtra("active", false)
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            running = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("steps_running", running)
            delay(1500)
        }
    }
    // Resume only when the user had previously enabled tracking; an explicit Stop disables this flag.
    LaunchedEffect(categoryId, permissionGranted) {
        val state = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
        if (permissionGranted && available && state.getBoolean("steps_enabled", false) && !state.getBoolean("steps_running", false)) startService()
    }

    val safeGoal = goal.coerceAtLeast(1)
    val progress = (count.toFloat() / safeGoal).coerceIn(0f, 1f)
    val distanceKm = count * 0.00075
    val calories = (count * 0.04).toInt()
    val today = LocalDate.now()
    val chartDates = when (range) {
        "30D" -> (29 downTo 0).map { today.minusDays(it.toLong()) }
        "MONTH" -> (1..today.lengthOfMonth()).map { today.withDayOfMonth(it) }
        else -> (6 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val totalsByDate = days.associate { it.date to it.total }
    val chartDays = chartDates.map { date -> DayTotal(date.toString(), totalsByDate[date.toString()] ?: 0) }
    val weekDays = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { date ->
        DayTotal(date.toString(), totalsByDate[date.toString()] ?: 0)
    }
    val groupedWalks = sessions.groupBy { it.date }.entries.take(8)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0 to if (english) "Today" else "आज", 1 to if (english) "Stats" else "आकडेवारी", 2 to if (english) "Goals" else "लक्ष्य").forEach { (tab, label) ->
                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = if (running) Color(0xFFE1F3E9) else MaterialTheme.colorScheme.surfaceVariant) {
                Text(if (running) (if (english) "AUTO ON" else "सुरू") else if (english) "PAUSED" else "थांबले",
                    color = if (running) Color(0xFF16764E) else ink.copy(alpha = .7f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        when (selectedTab) {
            0 -> {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (english) "TODAY'S WALK" else "आजचे चालणे", color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(today.dayOfWeek.name.take(3), color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        val ringTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 13.dp.toPx()
                                val arcSize = Size(size.width - stroke * 2, size.height - stroke * 2)
                                val topLeft = Offset(stroke, stroke)
                                drawArc(ringTrackColor, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                                drawArc(Color(0xFFD5A757), 135f, 270f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (english) "WALK STEPS" else "चालण्याची पावले", color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall)
                                Text(NumberFormat.getIntegerInstance().format(count), color = ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                                Text("${(progress * 100).toInt()}% " + if (english) "of goal" else "लक्ष्य पूर्ण", color = Color(0xFF9A7435), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (english) "GOAL" else "लक्ष्य", color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                                Text(NumberFormat.getIntegerInstance().format(goal), color = ink, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (english) "REMAINING" else "उर्वरित", color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                                Text(NumberFormat.getIntegerInstance().format((goal - count).coerceAtLeast(0)), color = ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Color(0xFFD5A757), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StepMetricCard(if (english) "DISTANCE" else "अंतर", String.format(java.util.Locale.getDefault(), "%.2f km", distanceKm), if (english) "Estimated" else "अंदाजे", ink, Modifier.weight(1f))
                    StepMetricCard(if (english) "CALORIES" else "कॅलरी", "$calories kcal", if (english) "Estimated" else "अंदाजे", ink, Modifier.weight(1f))
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp)) {
                        Text(if (english) "THIS WEEK" else "या आठवड्यात", color = ink, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Daily walking steps" else "दररोजची चालण्याची पावले", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        WeeklyChart(weekDays, Modifier.fillMaxWidth().height(145.dp))
                    }
                }
            }
            1 -> {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text(if (english) "YOUR ACTIVITY" else "तुमची चालण्याची नोंद", color = ink, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("7D" to if (english) "7 days" else "७ दिवस", "30D" to if (english) "30 days" else "३० दिवस", "MONTH" to if (english) "Month" else "महिना").forEach { (key, label) ->
                                FilterChip(selected = range == key, onClick = { onRangeChange(key) }, label = { Text(label) })
                            }
                        }
                        Text(if (english) when (range) { "30D" -> "LAST 30 DAYS"; "MONTH" -> "THIS MONTH"; else -> "LAST 7 DAYS" } else when (range) { "30D" -> "मागील ३० दिवस"; "MONTH" -> "हा महिना"; else -> "मागील ७ दिवस" },
                            color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        WeeklyChart(chartDays, Modifier.fillMaxWidth().height(185.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StepMetricCard(if (english) "TOTAL STEPS" else "एकूण पावले", NumberFormat.getIntegerInstance().format(chartDays.sumOf { it.total }), if (english) "Selected period" else "निवडलेला कालावधी", ink, Modifier.weight(1f))
                            StepMetricCard(if (english) "ACTIVE DAYS" else "सक्रिय दिवस", "${chartDays.count { it.total > 0 }} / ${chartDays.size}", if (english) "Days with steps" else "पावले असलेले दिवस", ink, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val active = chartDays.filter { it.total > 0 }
                            StepMetricCard(if (english) "AVERAGE" else "सरासरी", NumberFormat.getIntegerInstance().format(active.map { it.total }.average().takeIf { it.isFinite() }?.toInt() ?: 0), if (english) "Per active day" else "प्रति सक्रिय दिवस", ink, Modifier.weight(1f))
                            StepMetricCard(if (english) "BEST DAY" else "सर्वोत्तम दिवस", NumberFormat.getIntegerInstance().format(chartDays.maxOfOrNull { it.total } ?: 0), if (english) "Highest count" else "सर्वाधिक पावले", ink, Modifier.weight(1f))
                        }
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (english) "WALKING SESSIONS" else "चालण्याची सत्रे", color = ink, fontWeight = FontWeight.SemiBold)
                        if (groupedWalks.isEmpty()) Text(if (english) "No walking sessions in this period" else "या कालावधीत चालण्याची नोंद नाही", color = ink.copy(alpha = .58f))
                        groupedWalks.forEach { (date, records) ->
                            val totalSteps = records.sumOf { it.steps }
                            val totalMinutes = records.sumOf { (((it.endedAt ?: System.currentTimeMillis()) - it.startedAt).coerceAtLeast(0L) / 60000L).toInt() }
                            val gpsDistance = records.filter { it.gpsReliable }.sumOf { it.distanceMeters }
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(date, color = ink, fontWeight = FontWeight.SemiBold)
                                    Text(if (english) "${records.size} sessions · $totalSteps steps · $totalMinutes min" else "${records.size} सत्रे · $totalSteps पावले · $totalMinutes मि.", color = ink.copy(alpha = .62f), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(if (gpsDistance > 0.0) { if (gpsDistance >= 1000.0) "%.2f km".format(gpsDistance / 1000.0) else "${gpsDistance.toInt()} m" } else "—",
                                    color = ink.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            else -> {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (english) "DAILY STEP TARGET" else "दैनिक पावलांचे लक्ष्य", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(NumberFormat.getIntegerInstance().format(goal), color = ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(if (english) "steps per day" else "पावले प्रतिदिन", color = ink.copy(alpha = .65f))
                        Button(onClick = { goalText = goal.toString(); showGoalDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF31413A), contentColor = Color(0xFFF3EEE3))) {
                            Text(if (english) "Change goal" else "लक्ष्य बदला")
                        }
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(17.dp)) {
                        Text(if (english) "Your walking data stays on this device" else "तुमची चालण्याची नोंद याच फोनमध्ये सुरक्षित राहते", color = ink, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Android hardware step sensors provide the count. GPS is optional and is used only for reliable distance." else "पावले Android च्या हार्डवेअर स्टेप सेन्सरने मोजली जातात. GPS ऐच्छिक असून विश्वसनीय अंतरासाठीच वापरले जाते.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(if (english) "Daily step goal" else "दैनिक पावलांचे लक्ष्य") },
            text = { OutlinedTextField(value = goalText, onValueChange = { goalText = it.filter(Char::isDigit).take(6) }, label = { Text(if (english) "Steps" else "पावले") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { goalText.toIntOrNull()?.takeIf { it in 1000..100000 }?.let(onGoalChange); showGoalDialog = false }) { Text(if (english) "Save" else "जतन करा") } },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}

@Composable
private fun StepMetricCard(label: String, value: String, note: String, ink: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(note, color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class ChantPreset(val title: String, val devanagari: String, val latin: String, val deity: String = "Other")

private val chantPresets = listOf(
    ChantPreset("Ram", "राम", "Ram", "Ram"),
    ChantPreset("Sri Rama Ram Jai Jai Ram", "श्रीराम राम जय जय राम श्रीराम जय राम जय जय राम", "Sri Rama Ram Jai Jai Ram Sri Rama Jai Ram Jai Jai Ram", "Ram"),
    ChantPreset("Jai Jai Pandurang Hari", "जय जय पांडुरंग हरी", "Jai Jai Pandurang Hari", "Vishnu"),
    ChantPreset("Jai Shri Ram", "जय श्रीराम", "Jai Shri Ram", "Ram"),
    ChantPreset("Om Namah Shivaya", "ॐ नमः शिवाय", "Om Namah Shivaya", "Shiva"),
    ChantPreset("Maha Mrityunjaya Mantra", "ॐ त्र्यम्बकं यजामहे सुगन्धिं पुष्टिवर्धनम्। उर्वारुकमिव बन्धनान्मृत्योर्मुक्षीय मामृतात्॥", "Om Tryambakam Yajamahe Sugandhim Pushtivardhanam Urvarukamiva Bandhanan Mrityor Mukshiya Maamritat", "Shiva"),
    ChantPreset("Om Namo Bhagavate Vasudevaya", "ॐ नमो भगवते वासुदेवाय", "Om Namo Bhagavate Vasudevaya", "Krishna"),
    ChantPreset("Hare Krishna Mahamantra", "हरे कृष्ण हरे कृष्ण, कृष्ण कृष्ण हरे हरे। हरे राम हरे राम, राम राम हरे हरे॥", "Hare Krishna Hare Krishna Krishna Krishna Hare Hare Hare Rama Hare Rama Rama Rama Hare Hare", "Krishna"),
    ChantPreset("Om Gam Ganapataye Namaha", "ॐ गं गणपतये नमः", "Om Gam Ganapataye Namaha", "Ganesh"),
    ChantPreset("Gayatri Mantra", "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्॥", "Om Bhur Bhuvah Svah Tat Savitur Varenyam Bhargo Devasya Dhimahi Dhiyo Yo Nah Prachodayat", "Devi"),
    ChantPreset("Shri Ram Jai Ram", "श्री राम जय राम जय जय राम", "Shri Ram Jai Ram Jai Jai Ram", "Ram"),
    ChantPreset("Om Hanumate Namah", "ॐ हनुमते नमः", "Om Hanumate Namah", "Hanuman"),
    ChantPreset("Guru Mantra", "गुरुर्ब्रह्मा गुरुर्विष्णुः गुरुर्देवो महेश्वरः। गुरुः साक्षात् परब्रह्म तस्मै श्रीगुरवे नमः॥", "Gurur Brahma Gurur Vishnuh Gurur Devo Maheshwarah Guruh Sakshat Parabrahma Tasmai Shri Gurave Namah", "Guru"),
    ChantPreset("Om (Pranava)", "ॐ", "Om", "Other"),
    ChantPreset("Ram Krishna Hari", "राम कृष्ण हरी ॥", "Ram Krishna Hari", "Vishnu"),
    ChantPreset("Om Namo Narayanaya", "ॐ नमो नारायणाय ॥", "Om Namo Narayanaya", "Vishnu"),
    ChantPreset("Digambara Digambara", "दिगंबरा दिगंबरा श्रीपाद वल्लभ दिगंबरा ॥", "Digambara Digambara Shripada Vallabha Digambara", "Dattatreya"),
    ChantPreset("Om Aim Saraswatyai Namah", "ॐ ऐं सरस्वत्यै नमः ॥", "Om Aim Saraswatyai Namah", "Devi"),
    ChantPreset("Om Mahalakshmyai Namah", "ॐ महालक्ष्म्यै नमः ॥", "Om Mahalakshmyai Namah", "Devi"),
    ChantPreset("Vakratunda Mahakaya", "वक्रतुण्ड महाकाय सूर्यकोटि समप्रभ । निर्विघ्नं कुरु मे देव सर्वकार्येषु सर्वदा ॥", "Vakratunda Mahakaya Suryakoti Samaprabha Nirvighnam Kuru Me Deva Sarvakaryeshu Sarvada", "Ganesh"),
    ChantPreset("Pandurang Hari", "पांडुरंग हरी", "Pandurang Hari", "Vishnu"),
    ChantPreset("Pandurang", "पांडुरंग", "Pandurang", "Vishnu")
)

@Composable
fun AutoChantCounterScreen(categoryId: Long, count: Int, totalCount: Int, english: Boolean, ink: Color, onMantraChange: (String) -> Unit, onManualCount: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE) }
    var target by remember(categoryId) { mutableStateOf(prefs.getString("target_$categoryId", if (english) "Ram" else "राम") ?: "राम") }
    var language by remember(categoryId) { mutableStateOf(prefs.getString("language_$categoryId", if (english) "en" else "hi") ?: "hi") }
    var automatic by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_enabled", false)) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var matched by remember { mutableStateOf(false) }
    var matchedCount by remember { mutableIntStateOf(0) }
    var presetMenu by remember { mutableStateOf(false) }
    var selectedDeity by remember(categoryId) { mutableStateOf("All") }
    var addMantraDialog by remember { mutableStateOf(false) }
    var newMantraMr by remember { mutableStateOf("") }
    var newMantraEn by remember { mutableStateOf("") }
    var customMantras by remember(categoryId) {
        mutableStateOf(prefs.getStringSet("custom_mantras_$categoryId", emptySet()).orEmpty().map { saved ->
            val parts = saved.split("\u001F", limit = 2)
            if (parts.size == 2) ChantPreset(parts[1], parts[0], parts[1])
            else ChantPreset(saved, saved, saved)
        })
    }

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
                automatic = true
                running = true
                status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
            } catch (error: Exception) {
                running = false
                automatic = false
                status = when {
                    error is SecurityException -> if (english) "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक"
                    error.message?.contains("busy", true) == true -> if (english) "Microphone busy — another app or call is using it" else "मायक्रोफोन व्यस्त — दुसरे अॅप किंवा कॉल वापरत आहे"
                    else -> error.message ?: if (english) "Could not start" else "सुरू झाले नाही"
                }
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startService() else { automatic = false; status = if (english) "Notification permission denied" else "सूचना परवानगी नाकारली" }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            automatic = false
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startService()
        } else { automatic = false; running = false; status = if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली" }
    }
    DisposableEffect(context, categoryId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) != "chant") return
                if (intent.hasExtra("active")) running = intent.getBooleanExtra("active", false)
                intent.getStringExtra("message")?.let { status = it
                    if (it.contains("Microphone busy", true) || it.contains("Microphone unavailable", true) ||
                        it.contains("microphone permission", true) || it.contains("मायक्रोफोन व्यस्त") ||
                        it.contains("मायक्रोफोन उपलब्ध नाही") || it.contains("परवानगी आवश्यक")) {
                        automatic = false
                    }
                }
                intent.getStringExtra("transcript")?.let { transcript = it }
                if (intent.hasExtra("matched")) matched = intent.getBooleanExtra("matched", false)
                if (intent.hasExtra("delta")) {
                    val delta = intent.getIntExtra("delta", 0)
                    matched = true
                    matchedCount = delta
                }
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(categoryId, english, automatic) {
        var lastRetryAt = 0L
        while (true) {
            val active = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_enabled", false)
            running = active
            if (automatic && !active && System.currentTimeMillis() - lastRetryAt >= 5000L) {
                lastRetryAt = System.currentTimeMillis()
                status = if (english) "Recognition reconnecting…" else "आवाज ओळख पुन्हा जोडत आहे…"
                startService()
            }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "TODAY" else "आज", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (english) "Chants" else "जप", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(start = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "Voice count" else "आवाज मोजणी", color = ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = automatic, onCheckedChange = { enabled ->
                    if (!enabled) stopRecognition()
                    else {
                        automatic = false; matched = false; matchedCount = 0; transcript = ""
                        status = if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…"
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startService()
                    }
                })
            }
        }
        Card(Modifier.fillMaxWidth().heightIn(min = 760.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 760.dp).padding(horizontal = 18.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val malaProgress = if (totalCount > 0 && totalCount % 108 == 0) 108 else totalCount % 108
                val malaFraction = malaProgress / 108f
                val beadTint by animateColorAsState(if (malaProgress == 108) Color(0xFFB87928) else Color(0xFF7650A1), label = "beadCountTint")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFEAF3F9)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(totalCount.toString(), color = Color(0xFF285D87), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "TOTAL JAPA" else "एकूण जप", color = Color(0xFF285D87), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFF4EFF9)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$malaProgress/108", color = beadTint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "MALA PROGRESS" else "माळ प्रगती", color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4E4)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((totalCount / 108).toString(), color = Color(0xFFB87928), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "MALAS" else "पूर्ण माळा", color = Color(0xFF9A6828), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Text(target, color = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFF38234F) else ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp))
                val manualInteraction = remember { MutableInteractionSource() }
                val manualPressed by manualInteraction.collectIsPressedAsState()
                val manualScale by animateFloatAsState(if (manualPressed) 0.94f else 1f, label = "japaTapScale")
                val circleAccent by animateColorAsState(if (count > 0 && count % 108 == 0) Color(0xFFD5A24B) else Color(0xFFB895D7), label = "japaCircleAccent")
                Box(Modifier.padding(top = 16.dp).size(244.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { malaFraction },
                        modifier = Modifier.size(244.dp),
                        strokeWidth = 9.dp,
                        color = beadTint,
                        trackColor = Color(0xFFE8E1D7)
                    )
                    Box(
                        Modifier.size(218.dp)
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
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Ram", "Krishna", "Shiva", "Ganesh", "Hanuman", "Devi", "Vishnu", "Dattatreya", "Guru", "Other").forEach { deity ->
                        FilterChip(selected = selectedDeity == deity, onClick = { selectedDeity = deity }, label = { Text(if (english) deity else when(deity) { "All" -> "सर्व"; "Ram" -> "राम"; "Krishna" -> "कृष्ण"; "Shiva" -> "शिव"; "Ganesh" -> "गणेश"; "Hanuman" -> "हनुमान"; "Devi" -> "देवी"; "Vishnu" -> "विष्णू"; "Dattatreya" -> "दत्तात्रेय"; "Guru" -> "गुरु"; else -> "इतर" }) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { presetMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (english) "Choose mantra" else "मंत्र निवडा", maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            val visiblePresets = (chantPresets + customMantras).distinctBy { it.devanagari }
                                .filter { selectedDeity == "All" || it.deity == selectedDeity }
                            visiblePresets.forEach { item ->
                                val mantra = if (language == "hi") item.devanagari else item.latin
                                DropdownMenuItem(text = { Text(mantra, maxLines = 2) }, onClick = {
                                    target = mantra
                                    prefs.edit().putString("target_$categoryId", target).apply()
                                    onMantraChange(target)
                                    if (running) {
                                        transcript = ""
                                        matched = false
                                        matchedCount = 0
                                        status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
                                        ChantRecognitionService.start(context, categoryId, target.trim(), language)
                                    }
                                    presetMenu = false
                                })
                            }
                        }
                    }
                    Button(
                        onClick = { newMantraMr = ""; newMantraEn = ""; addMantraDialog = true },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (english) "+ Add" else "+ जोडा", maxLines = 1) }
                }
                if (automatic) {
                    val listeningOffline = running && (status.contains("Listening offline", true) || status.contains("Mantra matched", true) || status.contains("ऐकत आहे", true) || status.contains("मंत्र जुळला", true))
                    val micPulse = rememberInfiniteTransition(label = "offlineMicPulse")
                    val micScale by micPulse.animateFloat(
                        initialValue = 1f, targetValue = 1.18f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(650),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ), label = "micScale"
                    )
                    Surface(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (listeningOffline) Color(0xFFE4F5EA) else Color(0xFFF3EFF8)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (listeningOffline) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF16834F), modifier = Modifier.size(22.dp).graphicsLayer(scaleX = micScale, scaleY = micScale))
                                    Text(if (english) "LISTENING OFFLINE" else "ऑफलाइन ऐकत आहे", modifier = Modifier.weight(1f), color = Color(0xFF16834F), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF76528D), modifier = Modifier.size(20.dp))
                                    Text(if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख", modifier = Modifier.weight(1f), color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (status.contains("Preparing", true) || status.contains("तयार होत", true) || status.contains("Downloading", true) || status.contains("डाउनलोड", true) || status.contains("Extracting", true) || status.contains("Loading", true) || status.contains("लोड होत", true)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(if (english) when { status.contains("मॉडेल तयार", true) -> "Offline model ready…"; status.contains("ऐकत आहे", true) -> "Listening offline"; status.contains("माइक पुन्हा", true) -> "Reconnecting microphone…"; status.contains("ओळख यंत्रणा", true) -> "Preparing recognizer…"; else -> status.ifBlank { "Preparing offline model…" } } else status.ifBlank { "ऑफलाइन मॉडेल तयार होत आहे…" }, color = ink, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Box(Modifier.fillMaxWidth().clipToBounds().padding(top = 6.dp)) {
                                    AnimatedContent(
                                        targetState = transcript.ifBlank {
                                            if (listeningOffline) (if (english) "Listening offline" else status.ifBlank { "ऑफलाइन ऐकत आहे" })
                                            else if (english) when {
                                                status.contains("मॉडेल तयार", true) -> "Offline model ready"
                                                status.contains("माइक पुन्हा", true) -> "Reconnecting microphone…"
                                                status.contains("ओळख यंत्रणा", true) -> "Preparing recognizer…"
                                                else -> status.ifBlank { if (running) "Listening…" else "Starting…" }
                                            } else status.ifBlank { if (running) "ऐकत आहे…" else "सुरू होत आहे…" }
                                        },
                                        transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
                                        label = "recognizedPhraseTicker"
                                    ) { visibleText ->
                                        Text(visibleText,
                                            color = if (matched) (if (listeningOffline) Color(0xFF16834F) else ink).copy(alpha = 0.58f) else if (listeningOffline) Color(0xFF16834F) else ink,
                                            fontWeight = if (matched) FontWeight.Medium else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    }
                                }
                                if (matched) Text(
                                    (if (english) "Mantra matched" else "मंत्र जुळला") + if (matchedCount > 0) " · +$matchedCount" else "",
                                    color = Color(0xFF16834F), style = MaterialTheme.typography.labelSmall)
                            }
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
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = newMantraMr, onValueChange = { newMantraMr = it },
                        label = { Text("मंत्र (देवनागरी)") }, singleLine = false)
                    OutlinedTextField(value = newMantraEn, onValueChange = { newMantraEn = it },
                        label = { Text("Mantra (English)") }, singleLine = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val devanagari = newMantraMr.trim()
                    val latin = newMantraEn.trim()
                    if (devanagari.isNotBlank() && latin.isNotBlank()) {
                        val added = ChantPreset(latin, devanagari, latin)
                        customMantras = (customMantras + added).distinctBy { it.devanagari }
                        val saved = customMantras.map { it.devanagari + "\u001F" + it.latin }.toSet()
                        val selectedText = if (language == "hi") devanagari else latin
                        prefs.edit().putStringSet("custom_mantras_$categoryId", saved)
                            .putString("target_$categoryId", selectedText).apply()
                        target = selectedText
                        onMantraChange(selectedText)
                        if (running) {
                            transcript = ""
                            matched = false
                            matchedCount = 0
                            status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
                            ChantRecognitionService.start(context, categoryId, selectedText, language)
                        }
                    }
                    addMantraDialog = false
                }, enabled = newMantraMr.isNotBlank() && newMantraEn.isNotBlank()) { Text(if (english) "Add" else "जोडा") }
            },
            dismissButton = { TextButton(onClick = { addMantraDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}


@Composable
fun JapaReportScreen(logs: List<CounterLog>, english: Boolean, ink: Color, onBack: () -> Unit) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val totalsByDate = remember(logs) { logs.groupBy { it.date }.mapValues { (_, records) -> records.sumOf { it.count } } }
    val activeDates = remember(totalsByDate) { totalsByDate.filterValues { it > 0 }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }
    val total = logs.sumOf { it.count }
    val today = LocalDate.now()
    val todayCount = totalsByDate[today.toString()] ?: 0
    val average = if (activeDates.isNotEmpty()) total / activeDates.size else 0
    val best = totalsByDate.maxByOrNull { it.value }
    val currentStreak = run {
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in activeDates) { streak++; cursor = cursor.minusDays(1) }
        streak
    }
    val longestStreak = run {
        var run = 0
        var bestRun = 0
        var previous: LocalDate? = null
        activeDates.sorted().forEach { date ->
            run = if (previous?.plusDays(1) == date) run + 1 else 1
            bestRun = maxOf(bestRun, run)
            previous = date
        }
        bestRun
    }
    val weekStart = today.minusDays(6)
    val week = (0..6).map { date ->
        val day = weekStart.plusDays(it.toLong())
        DayTotal(day.toString(), totalsByDate[day.toString()] ?: 0)
    }
    val monthDays = month.lengthOfMonth()
    val offset = month.atDay(1).dayOfWeek.value % 7
    val monthCells = (0 until 42).map { cell -> if (cell - offset + 1 in 1..monthDays) cell - offset + 1 else null }
    val mantraBreakdown = logs.groupBy { it.note.removePrefix("MANTRA:") }.mapValues { (_, rows) -> rows.sumOf { it.count } }.entries.sortedByDescending { it.value }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowDropDown, null); Text(if (english) "Back to Japa" else "जपाकडे परत") }
            Spacer(Modifier.weight(1f))
            Text(if (english) "MY JAPA REPORT" else "माझा जप अहवाल", color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text(today.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")), color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "TOTAL JAPA" else "एकूण जप", NumberFormat.getIntegerInstance().format(total), if (english) "${total / 108} malas" else "${total / 108} माळा", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "TODAY" else "आज", NumberFormat.getIntegerInstance().format(todayCount), if (english) "Japa count" else "आजचा जप", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "CURRENT STREAK" else "सध्याची सलगता", "$currentStreak", if (english) "days" else "दिवस", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "LONGEST STREAK" else "सर्वाधिक सलगता", "$longestStreak", if (english) "days" else "दिवस", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "ACTIVE DAYS" else "सक्रिय दिवस", "${activeDates.size}", if (english) "All time" else "एकूण", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "AVERAGE / DAY" else "दररोज सरासरी", NumberFormat.getIntegerInstance().format(average), if (english) "Per active day" else "सक्रिय दिवस", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "BEST DAY" else "सर्वोत्तम दिवस", NumberFormat.getIntegerInstance().format(best?.value ?: 0), best?.key ?: "—", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "TOTAL MALAS" else "पूर्ण माळा", NumberFormat.getIntegerInstance().format(total / 108), if (english) "108 japa each" else "प्रत्येक माळ १०८", ink, Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (english) month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")) else month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                        TextButton(onClick = { month = month.plusMonths(1).coerceAtMost(today.withDayOfMonth(1)) }) { Text("›") }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (if (english) listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat") else listOf("रवि","सोम","मंगळ","बुध","गुरु","शुक्र","शनि")).forEach { label ->
                        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha=.6f))
                    }
                }
                monthCells.chunked(7).forEach { weekRow ->
                    Row(Modifier.fillMaxWidth()) {
                        weekRow.forEach { day ->
                            Box(Modifier.weight(1f).height(38.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val date = month.atDay(day)
                                    val value = totalsByDate[date.toString()] ?: 0
                                    Surface(shape = CircleShape, color = when { value >= 108 -> Color(0xFFE7D8F6); value > 0 -> Color(0xFFDDEEE4); else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f) }) {
                                        Text(day.toString(), modifier = Modifier.padding(7.dp), color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = if (value > 0) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(if (english) "Highlighted days have japa recorded. Purple = one or more malas." else "जप केलेले दिवस ठळक; जांभळा रंग म्हणजे एक किंवा अधिक माळा.", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text(if (english) "WEEKLY JAPA · 7 DAYS" else "साप्ताहिक जप · ७ दिवस", color = ink, fontWeight = FontWeight.SemiBold)
                Text(NumberFormat.getIntegerInstance().format(week.sumOf { it.total }) + (if (english) " japa" else " जप"), color = ink.copy(alpha=.65f), style = MaterialTheme.typography.bodySmall)
                WeeklyChart(week, Modifier.fillMaxWidth().height(160.dp))
                Text("${weekStart.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))} – ${today.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}", color = ink.copy(alpha=.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "JAPA BY MANTRA" else "मंत्रानुसार जप", color = ink, fontWeight = FontWeight.SemiBold)
                if (mantraBreakdown.isEmpty()) Text(if (english) "No japa records yet" else "अजून जप नोंद नाही", color = ink.copy(alpha=.6f))
                mantraBreakdown.forEach { (mantra, value) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mantra, color = ink, maxLines = 2)
                            Text("${value / 108} malas", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(NumberFormat.getIntegerInstance().format(value), color = Color(0xFF7855A6), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { if (total > 0) value.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF8B69B5), trackColor = MaterialTheme.colorScheme.surfaceVariant)
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
                        statusCallback("Ready")
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

