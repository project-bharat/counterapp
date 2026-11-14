package com.projectbharat.counterapp

import android.content.Intent
import android.app.TimePickerDialog
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.os.Bundle
import android.media.ToneGenerator
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectbharat.counterapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyReminderScheduler.rescheduleAll(this)
        SeedData.initialize(this)
        val repo = CounterRepository(CounterDatabase.get(this).dao())
        setContent { CounterApp(repo) }
    }
}

class CounterViewModel(private val repo: CounterRepository) : ViewModel() {
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun count(id: Long) = repo.today(id)
    fun week(id: Long) = repo.week(id)
    fun average(id: Long) = repo.average(id)
    fun averageBabyTimeToTen(id: Long) = repo.averageBabyTimeToTen(id)
    fun sessions(id: Long, date: String) = repo.babySessionsForDay(id, date)
    fun monthSessions(id: Long, month: LocalDate) = repo.babySessionsForMonth(id, month)
    fun latestSession(id: Long) = repo.latestBabySession(id)
    suspend fun startSession(id: Long) = repo.startBabySession(id)
    suspend fun addMovement(id: Long, sessionId: Long) = repo.addBabyMovement(id, sessionId, null)
    suspend fun undoMovement(sessionId: Long) = repo.undoBabyMovement(sessionId)
    suspend fun finishSession(sessionId: Long, strength: String?) = repo.finishBabySession(sessionId, strength)
    suspend fun completeSession(sessionId: Long) = repo.completeExpiredBabySession(sessionId)
    suspend fun addCategory(mr: String, en: String) = repo.addCategory(mr, en)
    suspend fun add(id: Long, strength: String?, duration: Long? = null) = repo.add(id, strength, duration)
    suspend fun undo(id: Long) = repo.undo(id)
    suspend fun reset(id: Long) = repo.reset(id)
    class Factory(private val repo: CounterRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CounterViewModel(repo) as T
    }
}

private val Navy = Color(0xFF102E55)
private val Gold = Color(0xFFE7A33C)
private val Cream = Color(0xFFF5F0E6)
private val SoftBlue = Color(0xFFDCE9F7)
private val zone = ZoneId.systemDefault()
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun sessionTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("hh:mm a"))

private fun periodLabel(period: String, english: Boolean): String = when (period) {
    "MORNING" -> if (english) "Morning" else "सकाळ"
    "AFTERNOON" -> if (english) "Afternoon" else "दुपार"
    "EVENING" -> if (english) "Evening" else "संध्याकाळ"
    else -> if (english) "Night" else "रात्र"
}

@Composable
fun CounterApp(repo: CounterRepository) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory(repo))
    val categories by vm.categories.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(false) }
    var monthlyHistory by remember { mutableStateOf(false) }
    var historyMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var addDialog by remember { mutableStateOf(false) }
    var remindersScreen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var resetDialog by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var finalStrength by remember { mutableStateOf("MEDIUM") }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clickTone = remember { ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 35) }
    DisposableEffect(clickTone) { onDispose { clickTone.release() } }
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(Unit) { while (true) { delay(1000); clock = System.currentTimeMillis() } }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }

    val countFlow = remember(selected?.id) { selected?.let { vm.count(it.id) } ?: flowOf(0) }
    val weekFlow = remember(selected?.id) { selected?.let { vm.week(it.id) } ?: flowOf(emptyList()) }
    val averageFlow = remember(selected?.id) { selected?.let { if (it.kind == "BABY") vm.averageBabyTimeToTen(it.id) else vm.average(it.id) } ?: flowOf(null) }
    val sessionFlow = remember(selected?.id, historyDate) { selected?.let { vm.sessions(it.id, historyDate.toString()) } ?: flowOf(emptyList()) }
    val latestFlow = remember(selected?.id) { selected?.let { vm.latestSession(it.id) } ?: flowOf(null) }
    val monthFlow = remember(selected?.id, historyMonth) { selected?.let { vm.monthSessions(it.id, historyMonth) } ?: flowOf(emptyList()) }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val average by averageFlow.collectAsStateWithLifecycle(null)
    val sessions by sessionFlow.collectAsStateWithLifecycle(emptyList())
    val latestSession by latestFlow.collectAsStateWithLifecycle(null)
    LaunchedEffect(latestSession?.id, clock) { latestSession?.let { if (it.endedAt == null && clock >= it.startedAt + CounterRepository.TWO_HOURS_MS) vm.completeSession(it.id) } }
    val monthSessions by monthFlow.collectAsStateWithLifecycle(emptyList())
    val isBaby = selected?.kind == "BABY"
    val activeSession = latestSession?.takeIf { it.endedAt == null && clock < it.startedAt + CounterRepository.TWO_HOURS_MS }
    val lockedSession = latestSession?.takeIf { clock < it.startedAt + CounterRepository.TWO_HOURS_MS }
    val remainingMs = activeSession?.let { (it.startedAt + CounterRepository.TWO_HOURS_MS - clock).coerceAtLeast(0L) } ?: 0L
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0D1B2A) else Cream
    val ink = if (dark) Color(0xFFEAF0F6) else Navy
    val tapSurface = if (dark) Color(0xFF23466A) else SoftBlue
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9FC7ED), secondary = Gold, background = page, surface = Color(0xFF172A3E))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)

    MaterialTheme(colorScheme = scheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(if (english) "DAILY COUNTER" else "दैनिक मोजणी", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        categories.forEach { cat ->
                            val glyph = when (cat.kind) { "BABY" -> Icons.Default.PregnantWoman; "MANTRA" -> Icons.Default.SelfImprovement; "STEPS" -> Icons.Default.DirectionsWalk; else -> Icons.Default.Add }
                            NavigationDrawerItem(
                                icon = { Icon(glyph, null) },
                                label = { Text(if (cat.kind == "MANTRA") { if (english) "Chants" else "जप / मंत्र" } else if (english) cat.titleEn else cat.titleMr) },
                                selected = selected?.id == cat.id && !remindersScreen,
                                onClick = { selectedId = cat.id; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } }
                            )
                        }
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text(if (english) "Add counter" else "नवीन मोजणी") }, selected = false, onClick = { addDialog = true; scope.launch { drawerState.close() } })
                        Divider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(icon = { Icon(Icons.Default.NotificationsActive, null) }, label = { Text(if (english) "Reminders" else "स्मरणपत्रे") }, selected = remindersScreen, onClick = { remindersScreen = true; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(if (english) "Weekly report" else "साप्ताहिक अहवाल") }, selected = weekly, onClick = { weekly = true; remindersScreen = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.History, null) }, label = { Text(if (english) "History" else "इतिहास") }, selected = monthlyHistory, onClick = { monthlyHistory = true; remindersScreen = false; weekly = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Language, null) }, label = { Text(if (english) "मराठी" else "English") }, selected = false, onClick = { english = !english; scope.launch { drawerState.close() } })
                    }
                }
            }
        ) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=14.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = ink) }
                    Column(Modifier.weight(1f)) {
                        Text(if(english) "YOUR DAILY RHYTHM" else "तुमची रोजची नोंद", color=ink.copy(alpha=.5f), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                        Text(if(english) "Daily Counter" else "दैनिक मोजणी", color=ink, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.ExtraBold)
                    }
                }

                Box(Modifier.weight(1f)) {
                if (remindersScreen) {
                    ReminderSettingsScreen(english = english, ink = ink)
                } else
                if (monthlyHistory && isBaby && selected != null) {
                    MonthlyHistoryScreen(
                        english = english, ink = ink, month = historyMonth, sessions = monthSessions,
                        onPrevious = { historyMonth = historyMonth.minusMonths(1) },
                        onNext = { if (historyMonth.isBefore(LocalDate.now().withDayOfMonth(1))) historyMonth = historyMonth.plusMonths(1) },
                        onExportPdf = { exportMonthlyPdf(context, selected.titleMr, selected.titleEn, historyMonth, monthSessions, english) }
                    )
                } else if (weekly) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(if (english) "TOTAL" else "एकूण", days.sumOf { it.total }.toString(), ink, Modifier.weight(1f))
                            StatTile(if (english) "DAILY AVG" else "दररोज सरासरी", "%.1f".format(days.sumOf { it.total }.toDouble() / 7.0), ink, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        WeeklyChart(days, Modifier.fillMaxWidth().height(210.dp))
                        if (isBaby) Text((if (english) "Average time to 10 movements: " else "१० हालचालींसाठी सरासरी वेळ: ") + (average?.let { "%.1f min".format(it / 60000.0) } ?: "—"))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val lines = buildString {
                                append(if (english) "Last 7 days\n" else "मागील ७ दिवसांची नोंद\n")
                                days.forEach { append("${it.date}: ${it.total}\n") }
                                append(if (english) "Total: " else "एकूण: "); append(days.sumOf { it.total })
                            }
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines) }
                            context.startActivity(Intent.createChooser(send, if (english) "Share summary" else "सारांश शेअर करा"))
                        }) { Text(if (english) "Share summary" else "सारांश शेअर करा") }
                    }
                } else if (isBaby && selected != null) {
                    BabyMovementScreen(
                        english = english,
                        ink = ink,
                        tapSurface = tapSurface,
                        latestSession = latestSession,
                        activeSession = activeSession,
                        lockedSession = lockedSession,
                        remainingMs = remainingMs,
                        sessions = sessions,
                        historyDate = historyDate,
                        onPreviousDay = { historyDate = historyDate.minusDays(1) },
                        onNextDay = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onStart = { scope.launch { vm.startSession(selected.id) } },
                        onTap = { sessionId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                            scope.launch { vm.addMovement(selected.id, sessionId) }
                        },
                        onUndo = { sessionId -> scope.launch { vm.undoMovement(sessionId) } },
                        onFinish = { finishDialog = true }
                    )
                } else if (selected != null) {
                    CounterTapScreen(
                        english = english, count = count, goal = selected.goal, kind = selected.kind,
                        categoryTitle = if (english) selected.titleEn else selected.titleMr,
                        ink = ink, onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65); scope.launch { vm.add(selected.id, null) } },
                        onUndo = { scope.launch { vm.undo(selected.id) } }, onReset = { resetDialog = true }
                    )
                }
                }
            }
            Text(if (english) "Designed & developed by Bharat Rasave" else "निर्मिती व संकल्पना · Bharat Rasave",
                color = ink.copy(alpha = .45f), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        }
    }

    if (addDialog) {
        var mr by remember { mutableStateOf("") }
        var en by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addDialog = false }, title = { Text(if (english) "New category" else "नवीन प्रकार") },
            text = { Column { OutlinedTextField(mr, { mr = it }, label = { Text("मराठी नाव") }); OutlinedTextField(en, { en = it }, label = { Text("English name") }) } },
            confirmButton = { TextButton(onClick = { if (mr.isNotBlank() || en.isNotBlank()) scope.launch { vm.addCategory(mr.ifBlank { en }, en.ifBlank { mr }) }; addDialog = false }) { Text(if (english) "Add" else "जोडा") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text(if (english) "Cancel" else "रद्द") } })
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text(if (english) "Reset today's count?" else "आजची मोजणी रीसेट करायची?") },
        confirmButton = { TextButton(onClick = { scope.launch { selected?.let { vm.reset(it.id) } }; resetDialog = false }) { Text(if (english) "Reset" else "रीसेट") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(if (english) "Cancel" else "रद्द") } })
    if (finishDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text(if (english) "Rate movement strength" else "बाळाच्या हालचालींची ताकद नोंदवा") },
            text = {
                Column {
                    Text(if (english) "How strong did the movements feel during this session?" else "या सत्रातील हालचालींची ताकद कशी जाणवली?")
                    listOf("LIGHT", "MEDIUM", "STRONG").forEach { value ->
                        val label = when (value) { "LIGHT" -> if (english) "Light" else "हलकी"; "MEDIUM" -> if (english) "Medium" else "मध्यम"; else -> if (english) "Strong" else "जोरात" }
                        Row(Modifier.fillMaxWidth().clickable { finalStrength = value }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = finalStrength == value, onClick = { finalStrength = value })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { vm.finishSession(activeSession.id, finalStrength) }; finishDialog = false }) { Text(if (english) "Save session" else "सत्र जतन करा") } },
            dismissButton = { TextButton(onClick = { finishDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}

@Composable
private fun BabyMovementScreen(
    english: Boolean, ink: Color, tapSurface: Color, latestSession: BabySession?, activeSession: BabySession?,
    lockedSession: BabySession?, remainingMs: Long, sessions: List<BabySession>, historyDate: LocalDate,
    onPreviousDay: () -> Unit, onNextDay: () -> Unit, onStart: () -> Unit, onTap: (Long) -> Unit,
    onUndo: (Long) -> Unit, onFinish: () -> Unit
) {
    val elapsed = activeSession?.let { (System.currentTimeMillis() - it.startedAt).coerceAtLeast(0L) } ?: 0L
    val progress = (elapsed.toFloat() / CounterRepository.TWO_HOURS_MS).coerceIn(0f, 1f)
    val tapScale = remember { Animatable(1f) }
    val tapScope = rememberCoroutineScope()
    val babyMotion = rememberInfiniteTransition(label = "babyMotion")
    val babyBob by babyMotion.animateFloat(initialValue = -4f, targetValue = 4f, animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "babyBob")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (activeSession != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (english) "BABY MOVEMENTS" else "बाळाची हालचाल", color = ink.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                    Text(periodLabel(activeSession.dayPeriod, english) + " · " + sessionTime(activeSession.startedAt), color=ink, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
                }
                Surface(shape=RoundedCornerShape(50), color=Color(0xFFE1F3E9)) {
                    Text(if (english) "● LIVE" else "● सुरू", modifier=Modifier.padding(horizontal=12.dp, vertical=7.dp), color=Color(0xFF16764E), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(28.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if (english) "MOVEMENTS COUNTED" else "नोंदवलेल्या हालचाली", color=ink.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                    Text(activeSession.movementCount.toString(), color=ink, style=MaterialTheme.typography.displayLarge, fontWeight=FontWeight.ExtraBold)
                    Text(if (english) "Kicks · Rolls · Nudges" else "लाथा · वळणे · हलके धक्के", color=ink.copy(alpha=.6f), style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(218.dp), contentAlignment=Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawCircle(tapSurface, style=androidx.compose.ui.graphics.drawscope.Stroke(width=15.dp.toPx()))
                            drawArc(Color(0xFFE7A33C), -90f, 360f*progress, false, style=androidx.compose.ui.graphics.drawscope.Stroke(width=15.dp.toPx(), cap=StrokeCap.Round))
                        }
                        val tapInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(178.dp)
                                .graphicsLayer(scaleX = tapScale.value, scaleY = tapScale.value, translationY = babyBob * 2f)
                                .background(Brush.linearGradient(listOf(Color(0xFFFFE6DD), Color(0xFFFFC9B9))), CircleShape)
                                .clickable(interactionSource = tapInteraction, indication = null) {
                                    tapScope.launch { tapScale.animateTo(1.16f, tween(110)); tapScale.animateTo(1f, tween(260)) }
                                    onTap(activeSession.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PregnantWoman, null, tint = Color(0xFFC65F68), modifier = Modifier.size(48.dp))
                                Text("+1", color = Color(0xFF7F3546), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                                Text(if (english) "TAP MOVEMENT" else "हालचाल मोजा", color = Color(0xFF7F3546), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Column {
                            Text(if (english) "ELAPSED" else "झालेला वेळ", color=ink.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall)
                            Text("%02d:%02d".format(elapsed/60000,(elapsed/1000)%60), color=ink, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text(if (english) "TIME LEFT" else "उर्वरित वेळ", color=ink.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall)
                            Text("%02d:%02d".format(remainingMs/3600000,(remainingMs/60000)%60), color=Gold, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                        }
                    }
                    LinearProgressIndicator(progress={progress}, modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(7.dp).clip(CircleShape), color=Gold, trackColor=tapSurface)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(onClick = { if (activeSession.movementCount > 0) onUndo(activeSession.id) },
                            enabled = activeSession.movementCount > 0, shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F3F8)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Undo, null, tint = ink, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (english) "Undo" else "मागे घ्या", color = ink, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (activeSession.movementCount >= 10) {
                Surface(Modifier.fillMaxWidth().padding(top = 9.dp), shape = RoundedCornerShape(15.dp), color = Color(0xFFE5F2E8)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF238451))
                            Spacer(Modifier.width(7.dp))
                            Text(if (english) "10+ movements" else "१०+ हालचाली", color = Color(0xFF238451), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(activeSession.movementCount.toString(), color = Color(0xFF238451), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                        LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(7.dp).clip(CircleShape), color = Color(0xFF2E9B63), trackColor = Color(0xFFB9DDC8))
                        Text(if (english) "10 reached · continue recording" else "१० पूर्ण · हालचालींची नोंद सुरू ठेवा", color = Color(0xFF238451), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            Text(if (english) "Session saves automatically after 2 hours" else "२ तासांनंतर सत्र आपोआप जतन होईल", color=ink.copy(alpha=.5f), style=MaterialTheme.typography.labelSmall, modifier=Modifier.padding(8.dp))
        } else {
            Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(26.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(66.dp).background(Color(0xFFFFE4E0),CircleShape),contentAlignment=Alignment.Center) { Icon(Icons.Default.Favorite,null,tint=Color(0xFFC65F68),modifier=Modifier.size(32.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (english) "Ready to begin?" else "सत्र सुरू करूया?", color=ink, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    Text(if (english) "Choose a time when baby is usually active." else "बाळ नेहमी सक्रिय असते ती वेळ निवडा.", color=ink.copy(alpha=.6f), style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick=onStart, modifier=Modifier.fillMaxWidth().height(52.dp), shape=RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PlayArrow,null); Spacer(Modifier.width(7.dp)); Text(if (english) "Start 2-hour session" else "२ तासांचे सत्र सुरू करा")
                    }
                }
            }
            if (lockedSession != null) {
                Surface(Modifier.fillMaxWidth().padding(top=9.dp), shape=RoundedCornerShape(15.dp), color=Color(0xFFFFF1D9)) {
                    Text(if (english) "Previous session · " + lockedSession.movementCount + " movements · Available at " + sessionTime(lockedSession.startedAt+CounterRepository.TWO_HOURS_MS) else "मागील सत्र · " + lockedSession.movementCount + " हालचाली · पुढील सत्र " + sessionTime(lockedSession.startedAt+CounterRepository.TWO_HOURS_MS), modifier=Modifier.padding(12.dp), color=Color(0xFF865B20), style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if (english) "SESSION HISTORY" else "सत्रांची नोंद", color=ink, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onPreviousDay, modifier=Modifier.size(30.dp)) { Text("‹",color=ink,style=MaterialTheme.typography.titleLarge) }
                Text(historyDate.format(dateFormat), color=ink, style=MaterialTheme.typography.labelMedium)
                IconButton(onClick=onNextDay, enabled=historyDate.isBefore(LocalDate.now()), modifier=Modifier.size(30.dp)) { Text("›",color=ink,style=MaterialTheme.typography.titleLarge) }
            }
        }
        if (sessions.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape=RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.surface) {
                Text(if (english) "No sessions on this day" else "या दिवसाची नोंद उपलब्ध नाही", modifier=Modifier.padding(16.dp), color=ink.copy(alpha=.55f), style=MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement=Arrangement.spacedBy(7.dp), contentPadding=PaddingValues(bottom=12.dp)) {
                items(sessions,key={it.id}) { session ->
                    Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(17.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(0xFFFFE8DC),CircleShape),contentAlignment=Alignment.Center) { Icon(Icons.Default.Favorite,null,tint=Color(0xFFC65F68)) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(periodLabel(session.dayPeriod,english) + " · " + sessionTime(session.startedAt), color=ink, fontWeight=FontWeight.Bold)
                                val duration=((session.endedAt ?: minOf(session.startedAt+CounterRepository.TWO_HOURS_MS,System.currentTimeMillis()))-session.startedAt).coerceAtLeast(0)/60000
                                Text(duration.toString()+" min · "+(if(session.endedAt!=null || System.currentTimeMillis()>=session.startedAt+CounterRepository.TWO_HOURS_MS) if(english) "Completed" else "पूर्ण" else if(english) "In progress" else "सुरू"), color=ink.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall)
                            }
                            Text(session.movementCount.toString(), color=Gold, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterTapScreen(english:Boolean,count:Int,goal:Int,kind:String,categoryTitle:String,ink:Color,onTap:()->Unit,onUndo:()->Unit,onReset:()->Unit) {
    val mantra = kind == "MANTRA"
    val steps = kind == "STEPS"
    val progress=if(goal>0) (count.toFloat()/goal).coerceIn(0f,1f) else 0f
    val tapScale = remember { Animatable(1f) }
    val tapScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).background(if(mantra) Color(0xFFEDE4FA) else Color(0xFFDDF1E8),CircleShape),contentAlignment=Alignment.Center) {
                    Icon(if(mantra) Icons.Default.SelfImprovement else Icons.Default.DirectionsWalk,null,tint=if(mantra) Color(0xFF7855A6) else Color(0xFF398A68),modifier=Modifier.size(30.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(if(english) "TODAY" else "आज",color=ink.copy(alpha=.55f),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                Text(count.toString(),color=ink,style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.ExtraBold)
                Text(if(mantra) { if(english) "CHANTS" else "जप" } else if(steps) { if(english) "STEPS" else "पावले" } else categoryTitle,color=ink.copy(alpha=.55f),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                if(mantra) {
                    Text((count/108).toString()+" "+(if(english) "mala" else "माळ")+" · "+(count%108)+"/108",color=Gold,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=9.dp))
                    Row(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.Center) { repeat(12) { index -> Box(Modifier.padding(3.dp).size(11.dp).background(if(index<count%108*12/108) Gold else Color(0xFFE8E1D7),CircleShape)) } }
                } else if (steps && goal > 0) {
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(if(english) "DAILY GOAL" else "दैनिक लक्ष्य",color=ink.copy(alpha=.6f),style=MaterialTheme.typography.labelSmall)
                        Text(count.toString()+" / "+goal,color=ink,fontWeight=FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(8.dp).clip(CircleShape),color=Color(0xFF55A886),trackColor=Color(0xFFDDEBE3))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val tapInteraction = remember { MutableInteractionSource() }
        Box(Modifier.size(214.dp).graphicsLayer(scaleX = tapScale.value, scaleY = tapScale.value).background(Brush.linearGradient(if(mantra) listOf(Color(0xFFE9DDF8),Color(0xFFD8C4F0)) else listOf(Color(0xFFDDF1E8),Color(0xFFBDE2D0))),CircleShape).clickable(interactionSource = tapInteraction, indication = null) { tapScope.launch { tapScale.animateTo(.90f, tween(75)); tapScale.animateTo(1.08f, tween(110)); tapScale.animateTo(1f, tween(180)) }; onTap() },contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(if(mantra) Icons.Default.SelfImprovement else Icons.Default.DirectionsWalk,null,tint=ink,modifier=Modifier.size(36.dp))
                Text("+1",color=ink,style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.ExtraBold)
                Text(if(english) "TAP TO ADD" else "मोजणी वाढवा",color=ink,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onUndo,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) { Icon(Icons.Default.Undo,null); Spacer(Modifier.width(5.dp)); Text(if(english) "Undo" else "मागे") }
            OutlinedButton(onClick=onReset,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) { Icon(Icons.Default.Refresh,null); Spacer(Modifier.width(5.dp)); Text(if(english) "Reset" else "रीसेट") }
        }
        Spacer(Modifier.weight(.8f))
    }
}


@Composable
private fun ReminderSettingsScreen(english: Boolean, ink: Color) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val settings = remember { mutableStateListOf<DailyReminder>().apply { addAll(DailyReminderScheduler.load(context)) } }
    Column(Modifier.fillMaxSize()) {
        Text(if (english) "DAILY REMINDERS" else "दैनिक स्मरणपत्रे", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (english) "Choose times that fit your routine." else "तुमच्या दिनक्रमानुसार वेळ निवडा.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            items(settings, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (english) item.titleEn else item.titleMr, color = ink, fontWeight = FontWeight.SemiBold)
                            Text("%02d:%02d".format(item.hour, item.minute), color = Color(0xFF39865B), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable {
                                    TimePickerDialog(context, { _, hour, minute ->
                                        val index = settings.indexOfFirst { it.id == item.id }
                                        if (index >= 0) {
                                            settings[index] = settings[index].copy(hour = hour, minute = minute)
                                            DailyReminderScheduler.saveAndSchedule(context, settings.toList())
                                        }
                                    }, item.hour, item.minute, false).show()
                                })
                            Text(if (english) "Tap time to change" else "वेळ बदलण्यासाठी टॅप करा", color = ink.copy(alpha = .45f), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { enabled ->
                            val index = settings.indexOfFirst { it.id == item.id }
                            if (index >= 0) {
                                settings[index] = settings[index].copy(enabled = enabled)
                                DailyReminderScheduler.saveAndSchedule(context, settings.toList())
                                if (enabled && Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4101) }
                                }
                            }
                        })
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF3DF)) {
            Text(
                if (english) "Baby movement reminders are optional routine prompts, not medical advice. Follow your maternity professional's guidance. If movements become reduced or change from your baby's usual pattern, contact your maternity unit promptly; do not wait for a reminder."
                else "बाळाच्या हालचालींची स्मरणपत्रे ही केवळ ऐच्छिक आठवण आहे, वैद्यकीय सल्ला नाही. डॉक्टरांच्या सूचनांचे पालन करा. हालचाली कमी झाल्या किंवा नेहमीच्या पद्धतीत बदल जाणवला तर स्मरणपत्राची वाट न पाहता त्वरित प्रसूती-सेवा केंद्राशी संपर्क साधा.",
                color = Color(0xFF76531F), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(13.dp)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
            Text(label, color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun WeeklyChart(days: List<DayTotal>, modifier: Modifier = Modifier) {
    val maxValue=(days.maxOfOrNull{it.total} ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                Text(day.total.toString(),color=Navy,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(.72f).weight(1f,false).height((145f*day.total/maxValue).coerceAtLeast(if(day.total>0) 8f else 3f).dp).clip(RoundedCornerShape(topStart=8.dp,topEnd=8.dp)).background(if(day.total==maxValue) Gold else Color(0xFF9BB6D3)))
                Spacer(Modifier.height(7.dp))
                Text(day.date.takeLast(2),color=Navy.copy(alpha=.65f),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MonthlyHistoryScreen(
    english: Boolean,
    ink: Color,
    month: LocalDate,
    sessions: List<BabySession>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExportPdf: () -> Unit
) {
    val totals = sessions.groupBy { it.date }.mapValues { entry -> entry.value.sumOf { it.movementCount } }
    val daysInMonth = month.lengthOfMonth()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onNext, enabled = month.isBefore(LocalDate.now().withDayOfMonth(1))) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HistoryMetric(if (english) "Sessions" else "सत्रे", sessions.size.toString(), ink)
            HistoryMetric(if (english) "Movements" else "हालचाली", sessions.sumOf { it.movementCount }.toString(), ink)
            HistoryMetric(if (english) "Avg/session" else "प्रति सत्र सरासरी", if (sessions.isEmpty()) "—" else "%.1f".format(sessions.map { it.movementCount }.average()), ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(if (english) "Daily movement pattern" else "दैनिक हालचालींचा आलेख", color = ink, style = MaterialTheme.typography.titleSmall)
        MonthlyChart((1..daysInMonth).map { day -> totals[month.withDayOfMonth(day).toString()] ?: 0 }, Modifier.fillMaxWidth().height(112.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onExportPdf, enabled = sessions.isNotEmpty()) { Text(if (english) "Share doctor PDF" else "डॉक्टरांसाठी PDF शेअर करा") }
        }
        Text(if (english) "Individual sessions" else "स्वतंत्र सत्रांची नोंद", color = ink, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        if (sessions.isEmpty()) {
            Text(if (english) "No sessions recorded this month" else "या महिन्यात सत्रे नोंदलेली नाहीत", color = ink.copy(alpha = .65f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${session.date}  •  ${periodLabel(session.dayPeriod, english)}  •  ${sessionTime(session.startedAt)}", color = ink, fontWeight = FontWeight.Medium)
                                val end = session.endedAt ?: (session.startedAt + CounterRepository.TWO_HOURS_MS)
                                val duration = ((end - session.startedAt).coerceAtLeast(0) / 60000)
                                Text((if (english) "Duration: " else "कालावधी: ") + "${duration} min" + (session.strength?.let { "  •  " + strengthLabel(it, english) } ?: ""), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                                session.timeToTenMs?.let { Text((if (english) "Time to 10: " else "१० हालचालींसाठी वेळ: ") + "${it / 60000} min", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(session.movementCount.toString(), color = Gold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, ink: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun strengthLabel(value: String, english: Boolean) = when (value) {
    "LIGHT" -> if (english) "Light" else "हलकी"
    "MEDIUM" -> if (english) "Medium" else "मध्यम"
    "STRONG" -> if (english) "Strong" else "जोरात"
    else -> value
}

@Composable
private fun MonthlyChart(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val gap = size.width / (values.size * 1.45f)
        val width = gap * .62f
        values.forEachIndexed { index, value ->
            val height = (size.height * .88f * value / max).coerceAtLeast(if (value > 0) 2f else 0f)
            drawRoundRect(Gold, androidx.compose.ui.geometry.Offset(index * gap + gap * .18f, size.height - height), androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(4f))
        }
        drawLine(Navy.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.5f)
    }
}

private fun exportMonthlyPdf(context: android.content.Context, titleMr: String, titleEn: String, month: LocalDate, sessions: List<BabySession>, english: Boolean) {
    val pdf = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdf.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = android.graphics.Color.DKGRAY }
    val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD); color = android.graphics.Color.rgb(16, 46, 85) }
    var y = 48f
    canvas.drawText(if (english) "Baby Movement Monthly Report" else "बाळाच्या हालचालींचा मासिक अहवाल", 36f, y, heading); y += 28f
    canvas.drawText(if (english) "Category: $titleEn" else "प्रकार: $titleMr", 36f, y, paint); y += 20f
    canvas.drawText("Month: ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}", 36f, y, paint); y += 22f
    canvas.drawText("Sessions: ${sessions.size}    Movements: ${sessions.sumOf { it.movementCount }}", 36f, y, paint); y += 28f
    canvas.drawText(if (english) "Date / Period / Start / Count / Duration / Strength" else "दिनांक / वेळ / सुरुवात / संख्या / कालावधी / ताकद", 36f, y, heading.apply { textSize = 11f }); y += 20f
    sessions.sortedBy { it.startedAt }.forEach { session ->
        if (y > 800f) return@forEach
        val end = session.endedAt ?: (session.startedAt + CounterRepository.TWO_HOURS_MS)
        val duration = ((end - session.startedAt).coerceAtLeast(0) / 60000)
        val row = "${session.date} | ${periodLabel(session.dayPeriod, true)} | ${sessionTime(session.startedAt)} | ${session.movementCount} | ${duration}m | ${session.strength ?: "—"}"
        canvas.drawText(row.take(100), 36f, y, paint); y += 17f
    }
    y += 20f
    canvas.drawText(if (english) "This report records logged sessions; it is not a medical assessment." else "हा अहवाल नोंदवलेल्या सत्रांचा सारांश आहे; वैद्यकीय मूल्यांकन नाही.", 36f, y, paint)
    pdf.finishPage(page)
    val file = File(context.cacheDir, "baby-movement-${month.year}-${month.monthValue}.pdf")
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, if (english) "Share doctor report" else "अहवाल शेअर करा"))
}
