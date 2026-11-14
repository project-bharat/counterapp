package com.projectbharat.counterapp

import android.content.Intent
import android.app.TimePickerDialog
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.os.Bundle
import android.media.ToneGenerator
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onStop() {
        StepTrackingService.stop(this)
        ChantRecognitionService.stop(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopService(Intent(this, StepTrackingService::class.java))
        stopService(Intent(this, ChantRecognitionService::class.java))
        getSharedPreferences("tracking_state", MODE_PRIVATE).edit()
            .putBoolean("steps_running", false).putBoolean("steps_enabled", false).putBoolean("chant_enabled", false).apply()
        DailyReminderScheduler.rescheduleAll(this)
        SeedData.initialize(this)
        val repo = CounterRepository(CounterDatabase.get(this).dao())
        setContent { CounterApp(repo) }
    }
}

class CounterViewModel(private val repo: CounterRepository) : ViewModel() {
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archivedCategories = repo.archivedCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun count(id: Long) = repo.today(id)
    fun logsForDay(id: Long, date: LocalDate) = repo.logsForDay(id, date)
    suspend fun updateLog(log: CounterLog) = repo.updateLog(log)
    suspend fun deleteLog(id: Long) = repo.deleteLog(id)
    fun week(id: Long) = repo.week(id)
    fun average(id: Long) = repo.average(id)
    fun averageBabyTimeToTen(id: Long) = repo.averageBabyTimeToTen(id)
    fun sessions(id: Long, date: String) = repo.babySessionsForDay(id, date)
    fun monthSessions(id: Long, month: LocalDate) = repo.babySessionsForMonth(id, month)
    fun latestSession(id: Long) = repo.latestBabySession(id)
    fun detections(sessionId: Long) = repo.babyDetections(sessionId)
    fun detectionsForDay(categoryId: Long, date: String) = repo.babyDetectionsForDay(categoryId, date)
    suspend fun recordDetection(sessionId: Long, timestamp: Long, magnitude: Float) = repo.recordBabyDetection(sessionId, timestamp, magnitude)
    suspend fun confirmDetection(id: Long) = repo.confirmBabyDetection(id)
    suspend fun dismissDetection(id: Long) = repo.dismissBabyDetection(id)
    suspend fun startSession(id: Long, mode: String) = repo.startBabySession(id, mode)
    suspend fun addMovement(id: Long, sessionId: Long) = repo.addBabyMovement(id, sessionId, null)
    suspend fun undoMovement(sessionId: Long) = repo.undoBabyMovement(sessionId)
    suspend fun finishSession(sessionId: Long, strength: String?) = repo.finishBabySession(sessionId, strength)
    suspend fun resumeSession(sessionId: Long) = repo.resumeBabySession(sessionId)
    suspend fun completeSession(sessionId: Long) = repo.completeExpiredBabySession(sessionId)
    suspend fun addCategory(mr: String, en: String) = repo.addCategory(mr, en)
    suspend fun updateCategory(category: CounterCategory) = repo.updateCategory(category)
    suspend fun archiveCategory(id: Long) = repo.archiveCategory(id)
    suspend fun restoreCategory(id: Long) = repo.restoreCategory(id)
    suspend fun add(id: Long, strength: String?, duration: Long? = null) = repo.add(id, strength, duration)
    suspend fun addCount(id: Long, amount: Int) = repo.addCount(id, amount)
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
    val archivedCategories by vm.archivedCategories.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(false) }
    var monthlyHistory by remember { mutableStateOf(false) }
    var historyMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var addDialog by remember { mutableStateOf(false) }
    var remindersScreen by remember { mutableStateOf(false) }
    var categoryScreen by remember { mutableStateOf(false) }
    var backupScreen by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var resetDialog by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var finalStrength by remember { mutableStateOf("MEDIUM") }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportJson(context, uri) } }
                .onSuccess { backupMessage = if (english) "Backup saved successfully." else "बॅकअप यशस्वीपणे जतन झाला." }
                .onFailure { backupMessage = (if (english) "Backup failed: " else "बॅकअप अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportCsv(context, uri) } }
                .onSuccess { backupMessage = if (english) "CSV exported successfully." else "CSV निर्यात यशस्वी." }
                .onFailure { backupMessage = (if (english) "CSV export failed: " else "CSV निर्यात अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }
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
    val historyLogsFlow = remember(selected?.id, historyDate) { selected?.let { vm.logsForDay(it.id, historyDate) } ?: flowOf(emptyList()) }
    val latestFlow = remember(selected?.id) { selected?.let { vm.latestSession(it.id) } ?: flowOf(null) }
    val monthFlow = remember(selected?.id, historyMonth) { selected?.let { vm.monthSessions(it.id, historyMonth) } ?: flowOf(emptyList()) }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val average by averageFlow.collectAsStateWithLifecycle(null)
    val sessions by sessionFlow.collectAsStateWithLifecycle(emptyList())
    val historyLogs by historyLogsFlow.collectAsStateWithLifecycle(emptyList())
    val latestSession by latestFlow.collectAsStateWithLifecycle(null)
    LaunchedEffect(latestSession?.id, clock) { latestSession?.let { val limit = if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS; if (it.endedAt == null && clock >= it.startedAt + limit) vm.completeSession(it.id) } }
    val monthSessions by monthFlow.collectAsStateWithLifecycle(emptyList())
    val isBaby = selected?.kind == "BABY"
    val activeSession = latestSession?.takeIf { val limit = if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS; it.endedAt == null && clock < it.startedAt + limit }
    val resumableSession = latestSession?.takeIf { val limit = if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS; it.endedAt != null && !(it.trackingMode == "TEN_MOVEMENTS" && it.movementCount >= 10) && clock < it.startedAt + limit }
    val lockedSession = latestSession?.takeIf { val limit = if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS; clock < it.startedAt + limit }
    val remainingMs = activeSession?.let { val limit = if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS; (it.startedAt + limit - clock).coerceAtLeast(0L) } ?: 0L
    val detectionFlow = remember(selected?.id, historyDate) { selected?.let { vm.detectionsForDay(it.id, historyDate.toString()) } ?: flowOf(emptyList()) }
    val detections by detectionFlow.collectAsStateWithLifecycle(emptyList())
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
                                onClick = { selectedId = cat.id; remindersScreen = false; weekly = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } }
                            )
                        }
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text(if (english) "Add counter" else "नवीन मोजणी") }, selected = false, onClick = { addDialog = true; scope.launch { drawerState.close() } })
                        Divider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(icon = { Icon(Icons.Default.NotificationsActive, null) }, label = { Text(if (english) "Reminders" else "स्मरणपत्रे") }, selected = remindersScreen, onClick = { remindersScreen = true; weekly = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Edit, null) }, label = { Text(if (english) "Manage counters" else "मोजणी व्यवस्थापन") }, selected = categoryScreen, onClick = { categoryScreen = true; backupScreen = false; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Save, null) }, label = { Text(if (english) "Backup & export" else "बॅकअप व निर्यात") }, selected = backupScreen, onClick = { backupScreen = true; categoryScreen = false; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(if (english) "Weekly report" else "साप्ताहिक अहवाल") }, selected = weekly, onClick = { weekly = true; remindersScreen = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.History, null) }, label = { Text(if (english) "History" else "इतिहास") }, selected = monthlyHistory, onClick = { monthlyHistory = true; remindersScreen = false; weekly = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Language, null) }, label = { Text(if (english) "मराठी" else "English") }, selected = false, onClick = { english = !english; scope.launch { drawerState.close() } })
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (english) "Designed & developed by Bharat Rasave" else "निर्मिती व संकल्पना · Bharat Rasave",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        ) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF5C0F57), Color(0xFF9B4B91))), RoundedCornerShape(22.dp))
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(if(english) "YOUR DAILY RHYTHM" else "तुमची रोजची नोंद", color=Color.White.copy(alpha=.76f), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                        Text(if(english) "Daily Counter" else "दैनिक मोजणी", color=Color.White, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Box(Modifier.weight(1f)) {
                if (backupScreen) {
                    BackupSettingsScreen(english = english, ink = ink, message = backupMessage,
                        onExportBackup = { jsonExportLauncher.launch("daily-counter-backup.json") },
                        onImportBackup = { jsonImportLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportCsv = { csvExportLauncher.launch("daily-counter-history.csv") })
                } else if (categoryScreen) {
                    CategoryManagementScreen(categories = categories, archivedCategories = archivedCategories, english = english, ink = ink,
                        onAdd = { mr, en -> scope.launch { vm.addCategory(mr, en) } },
                        onUpdate = { item -> scope.launch { vm.updateCategory(item) } },
                        onArchive = { item -> scope.launch { runCatching { vm.archiveCategory(item.id) }.onFailure { backupMessage = it.message } } },
                        onRestore = { item -> scope.launch { vm.restoreCategory(item.id) } })
                } else if (remindersScreen) {
                    ReminderSettingsScreen(english = english, ink = ink)
                } else
                if (monthlyHistory && isBaby && selected != null) {
                    MonthlyHistoryScreen(
                        english = english, ink = ink, month = historyMonth, sessions = monthSessions,
                        onPrevious = { historyMonth = historyMonth.minusMonths(1) },
                        onNext = { if (historyMonth.isBefore(LocalDate.now().withDayOfMonth(1))) historyMonth = historyMonth.plusMonths(1) },
                        onExportPdf = { exportMonthlyPdf(context, selected.titleMr, selected.titleEn, historyMonth, monthSessions, english) }
                    )
                } else if (monthlyHistory && selected != null) {
                    CounterHistoryScreen(
                        english = english, ink = ink, date = historyDate, logs = historyLogs,
                        onPrevious = { historyDate = historyDate.minusDays(1) },
                        onNext = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onUpdate = { log -> scope.launch { vm.updateLog(log) } },
                        onDelete = { log -> scope.launch { vm.deleteLog(log.id) } }
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
                        resumableSession = resumableSession,
                        lockedSession = lockedSession,
                        remainingMs = remainingMs,
                        sessions = sessions,
                        detections = detections,
                        historyDate = historyDate,
                        onPreviousDay = { historyDate = historyDate.minusDays(1) },
                        onNextDay = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onStart = { mode -> scope.launch { vm.startSession(selected.id, mode) } },
                        onResume = { sessionId -> scope.launch { vm.resumeSession(sessionId) } },
                        onTap = { sessionId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                            scope.launch { vm.addMovement(selected.id, sessionId) }
                        },
                        onUndo = { sessionId -> scope.launch { vm.undoMovement(sessionId) } },
                        onFinish = { finishDialog = true },
                        onAutoMovement = { sessionId, timestamp, magnitude -> scope.launch { vm.recordDetection(sessionId, timestamp, magnitude) } },
                        onConfirmDetections = { ids -> scope.launch { ids.forEach { vm.confirmDetection(it) } } },
                        onDismissDetections = { ids -> scope.launch { ids.forEach { vm.dismissDetection(it) } } }
                    )
                } else if (selected?.kind == "STEPS") {
                    AutoStepCounterScreen(
                        categoryId = selected.id, count = count, english = english, ink = ink
                    )
                } else if (selected?.kind == "MANTRA") {
                    AutoChantCounterScreen(
                        categoryId = selected.id, count = count, english = english, ink = ink,
                        onManualCount = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65); scope.launch { vm.add(selected.id, null) } }
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
        }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (english) "Replace app data?" else "सध्याचा डेटा बदलायचा?") },
            text = { Text(if (english) "Restoring replaces all counters, history and baby movement sessions on this device. Export a backup first if you need to keep the current data." else "बॅकअप पुनर्संचयित केल्याने या उपकरणावरील सर्व मोजण्या, इतिहास आणि बाळाच्या हालचालींची सत्रे बदलली जातील. सध्याचा डेटा हवा असल्यास आधी बॅकअप घ्या.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        backupMessage = null
                        runCatching { withContext(Dispatchers.IO) { CounterBackupManager.restoreJson(context, uri) } }
                            .onSuccess { backupMessage = if (english) "Backup restored successfully." else "बॅकअप यशस्वीपणे पुनर्संचयित झाला." }
                            .onFailure { backupMessage = (if (english) "Restore failed: " else "पुनर्संचयित करणे अयशस्वी: ") + (it.message ?: "Unknown error") }
                    }
                }) { Text(if (english) "Replace data" else "डेटा बदला") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
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
    resumableSession: BabySession?, lockedSession: BabySession?, remainingMs: Long, sessions: List<BabySession>, detections: List<BabyDetection>, historyDate: LocalDate,
    onPreviousDay: () -> Unit, onNextDay: () -> Unit, onStart: (String) -> Unit, onResume: (Long) -> Unit, onTap: (Long) -> Unit,
    onUndo: (Long) -> Unit, onFinish: () -> Unit, onAutoMovement: (Long, Long, Float) -> Unit,
    onConfirmDetections: (List<Long>) -> Unit, onDismissDetections: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    val elapsed = activeSession?.let { (System.currentTimeMillis() - it.startedAt).coerceAtLeast(0L) } ?: 0L
    val sessionLimit = activeSession?.let { if (it.trackingMode == "ONE_HOUR") CounterRepository.ONE_HOUR_MS else CounterRepository.TWO_HOURS_MS } ?: CounterRepository.TWO_HOURS_MS
    val progress = (elapsed.toFloat() / sessionLimit).coerceIn(0f, 1f)
    val tapScale = remember { Animatable(1f) }
    val tapScope = rememberCoroutineScope()
    var selectedTrackingMode by remember { mutableStateOf("TEN_MOVEMENTS") }
    var autoDetect by remember(activeSession?.id) { mutableStateOf(false) }
    var autoStatus by remember(activeSession?.id) { mutableStateOf("") }
    val pending = detections.count { it.status == "PENDING" }
    val added = detections.count { it.status == "CONFIRMED" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeSession != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (english) "BABY MOVEMENTS" else "बाळाची हालचाल", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(periodLabel(activeSession.dayPeriod, english) + " · " + sessionTime(activeSession.startedAt), color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (activeSession.trackingMode == "ONE_HOUR") { if (english) "1-hour movement count" else "१ तासातील हालचाली" } else { if (english) "Time to 10 movements" else "१० हालचालींसाठी लागणारा वेळ" }, color = Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (english) "Auto detect" else "स्वयंचलित", color = ink, style = MaterialTheme.typography.labelSmall)
                    Switch(checked = autoDetect, onCheckedChange = { autoDetect = it })
                }
            }
            BabyMotionSensorEngine(
                activeSessionId = activeSession.id, enabled = autoDetect,
                onDetected = { timestamp, magnitude -> onAutoMovement(activeSession.id, timestamp, magnitude) },
                onStatus = { autoStatus = it }
            )
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (english) "MOVEMENTS" else "हालचाली", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(activeSession.movementCount.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(tapSurface, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx()))
                            drawArc(Gold, -90f, 360f * progress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
                        }
                        val tapInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(174.dp)
                                .graphicsLayer(scaleX = tapScale.value, scaleY = tapScale.value)
                                .background(Brush.linearGradient(listOf(Color(0xFFFFE6DD), Color(0xFFFFC9B9))), CircleShape)
                                .clickable(interactionSource = tapInteraction, indication = null) {
                                    tapScope.launch { tapScale.animateTo(.92f, tween(90)); tapScale.animateTo(1f, tween(180)) }
                                    onTap(activeSession.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PregnantWoman, null, tint = Color(0xFFC65F68), modifier = Modifier.size(44.dp))
                                Text("+1", color = Color(0xFF7F3546), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                                Text(if (english) "TAP TO COUNT" else "स्पर्श करून मोजा", color = Color(0xFF7F3546), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("%02d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60), color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("%02d:%02d".format(remainingMs / 3600000, (remainingMs / 60000) % 60), color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(6.dp).clip(CircleShape), color = Gold, trackColor = tapSurface)
                    if (activeSession.trackingMode == "TEN_MOVEMENTS" && activeSession.timeToTenMs != null) {
                        Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F4EC)) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (english) "10 MOVEMENTS REACHED" else "१० हालचाली पूर्ण", color = Color(0xFF246B48), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(if (english) "Time: " + "%.1f min".format(activeSession.timeToTenMs / 60000.0) else "वेळ: " + "%.1f मिनिटे".format(activeSession.timeToTenMs / 60000.0), color = Color(0xFF246B48), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { if (activeSession.movementCount > 0) onUndo(activeSession.id) }, enabled = activeSession.movementCount > 0) {
                            Icon(Icons.Default.Undo, null); Spacer(Modifier.width(4.dp)); Text(if (english) "Undo" else "मागे")
                        }
                        TextButton(onClick = onFinish) { Text(if (english) "Finish session" else "सत्र पूर्ण करा") }
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PregnantWoman, null, tint = Color(0xFFC65F68), modifier = Modifier.size(42.dp))
                    Text(if (english) "Baby movement" else "बाळाची हालचाल", color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (resumableSession != null) {
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = { onResume(resumableSession.id) }, modifier = Modifier.height(46.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Resume session" else "सत्र पुन्हा सुरू करा")
                        }
                    } else if (lockedSession == null) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = selectedTrackingMode == "TEN_MOVEMENTS", onClick = { selectedTrackingMode = "TEN_MOVEMENTS" }, label = { Text(if (english) "Time to 10" else "१० हालचाली") })
                            FilterChip(selected = selectedTrackingMode == "ONE_HOUR", onClick = { selectedTrackingMode = "ONE_HOUR" }, label = { Text(if (english) "1 hour" else "१ तास") })
                        }
                        Text(if (selectedTrackingMode == "ONE_HOUR") { if (english) "Count every movement for one hour." else "एका तासात जाणवणाऱ्या प्रत्येक हालचालीची नोंद करा." } else { if (english) "Count until 10 movements; the time is saved automatically." else "१० हालचाली होईपर्यंत मोजा; लागलेला वेळ आपोआप जतन होईल." }, color = ink.copy(alpha=.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        Button(onClick = { onStart(selectedTrackingMode) }, modifier = Modifier.height(46.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Start session" else "सत्र सुरू करा")
                        }
                    }
                }
            }
            if (lockedSession != null && resumableSession == null) {
                Text(if (english) "Next session at " + sessionTime(lockedSession.startedAt + CounterRepository.TWO_HOURS_MS) else "पुढील सत्र " + sessionTime(lockedSession.startedAt + CounterRepository.TWO_HOURS_MS), color = ink.copy(alpha=.6f), modifier = Modifier.padding(10.dp))
            }
        }
        if (detections.isNotEmpty() || autoDetect) {
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(if (english) "DETECTED MOMENTS" else "ओळखलेले क्षण", color = ink, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Column {
                            Text(if (english) "Detected" else "ओळखले", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
                            Text(pending.toString(), color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        Column {
                            Text(if (english) "Added" else "जोडले", color = Color(0xFF238451), style = MaterialTheme.typography.labelSmall)
                            Text(added.toString(), color = Color(0xFF238451), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (pending > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirmDetections(detections.filter { it.status == "PENDING" }.map { it.id }) }, modifier = Modifier.weight(1f)) {
                                Text(if (english) "Add all" else "सर्व जोडा")
                            }
                            OutlinedButton(onClick = { onDismissDetections(detections.filter { it.status == "PENDING" }.map { it.id }) }, modifier = Modifier.weight(1f)) {
                                Text(if (english) "Dismiss" else "वगळा")
                            }
                        }
                    }
                    if (autoDetect && autoStatus.isNotBlank()) Text(autoStatus, color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (english) "HISTORY" else "इतिहास", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousDay, modifier = Modifier.size(32.dp)) { Text("‹", color = ink, style = MaterialTheme.typography.titleLarge) }
                Text(historyDate.format(dateFormat), color = ink, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onNextDay, enabled = historyDate.isBefore(LocalDate.now()), modifier = Modifier.size(32.dp)) { Text("›", color = ink, style = MaterialTheme.typography.titleLarge) }
            }
        }
        if (sessions.isEmpty()) {
            Text(if (english) "No sessions" else "नोंद उपलब्ध नाही", modifier = Modifier.fillMaxWidth().padding(14.dp), color = ink.copy(alpha=.55f))
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                sessions.forEach { session ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(periodLabel(session.dayPeriod, english) + " · " + sessionTime(session.startedAt), color = ink, fontWeight = FontWeight.Bold)
                                val duration = ((session.endedAt ?: minOf(session.startedAt + CounterRepository.TWO_HOURS_MS, System.currentTimeMillis())) - session.startedAt).coerceAtLeast(0) / 60000
                                Text(duration.toString() + " min · " + (if (session.trackingMode == "ONE_HOUR") { if (english) "1-hour count" else "१ तास" } else { if (english) "Count to 10" else "१० हालचाली" }), color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
                                if (session.timeToTenMs != null) Text((if (english) "10 in " else "१० साठी ") + "%.1f min".format(session.timeToTenMs / 60000.0), color = Color(0xFF238451), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(session.movementCount.toString(), color = Gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))) {
            Column(Modifier.padding(13.dp)) {
                Text(if (english) "A GENTLE REMINDER" else "छोटीशी आठवण", color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(if (english) "Choose a quiet time when you usually notice movement. Sit comfortably and count kicks, flutters, rolls or swishes. Follow the counting method recommended by your maternity-care professional." else "बाळाची हालचाल सहसा जाणवते अशी शांत वेळ निवडा. आरामात बसा आणि लाथा, फडफड, वळणे किंवा सरकणे यांची नोंद करा. तुमच्या डॉक्टरांनी सांगितलेली मोजणी पद्धत पाळा.", color = ink.copy(alpha=.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text(if (english) "If movements become less frequent or feel different from your baby's usual pattern, contact your maternity-care professional promptly. Do not wait for the timer." else "हालचाली नेहमीपेक्षा कमी किंवा वेगळ्या वाटल्यास वेळ संपण्याची वाट न पाहता त्वरित डॉक्टरांशी संपर्क साधा.", color = Color(0xFF9A5B36), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://my.clevelandclinic.org/health/articles/23497-kick-counts")); runCatching { context.startActivity(intent) } }) { Text(if (english) "Know how · Cleveland Clinic" else "अधिक जाणून घ्या · Cleveland Clinic") }
            }
        }
        Spacer(Modifier.height(12.dp))
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
    val width = 595
    val height = 842
    val navy = android.graphics.Color.rgb(16, 46, 85)
    val blue = android.graphics.Color.rgb(58, 112, 163)
    val gold = android.graphics.Color.rgb(231, 163, 60)
    val pale = android.graphics.Color.rgb(239, 245, 251)
    val ink = android.graphics.Color.rgb(45, 57, 72)
    val muted = android.graphics.Color.rgb(105, 119, 136)
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val sorted = sessions.sortedBy { it.startedAt }
    val total = sessions.sumOf { it.movementCount }
    val average = if (sessions.isEmpty()) 0.0 else sessions.map { it.movementCount }.average()
    val daily = (1..month.lengthOfMonth()).map { day ->
        sessions.filter { it.date == month.withDayOfMonth(day).toString() }.sumOf { it.movementCount }
    }
    val maxDaily = (daily.maxOrNull() ?: 0).coerceAtLeast(1)
    fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val titlePaint = paint(android.graphics.Color.WHITE, 21f, true)
    val subPaint = paint(android.graphics.Color.rgb(224, 234, 245), 10f)
    val bodyPaint = paint(ink, 9.5f)
    val smallPaint = paint(muted, 8.5f)
    val headPaint = paint(android.graphics.Color.WHITE, 9f, true)
    val numberPaint = paint(navy, 17f, true)
    val labelPaint = paint(muted, 8.5f, true)
    val footerPaint = paint(muted, 8f)
    val xs = floatArrayOf(36f, 120f, 196f, 268f, 328f, 403f)
    var pageNumber = 0
    var currentPage: PdfDocument.Page? = null
    var cv: android.graphics.Canvas? = null
    var y = 0f

    fun tableHeader(canvas: android.graphics.Canvas, top: Float): Float {
        canvas.drawRoundRect(android.graphics.RectF(36f, top, 559f, top + 28f), 7f, 7f, paint(navy, 9f, true))
        val labels = if (english) listOf("DATE", "PERIOD", "START", "METHOD", "MOVES", "TIME TO 10")
                     else listOf("दिनांक", "वेळ", "सुरुवात", "पद्धत", "हालचाली", "१० साठी वेळ")
        labels.forEachIndexed { i, label -> canvas.drawText(label, xs[i] + 7f, top + 18f, headPaint) }
        return top + 28f
    }
    fun startPage(first: Boolean) {
        pageNumber++
        currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        cv = currentPage!!.canvas
        val canvas = cv!!
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRoundRect(android.graphics.RectF(24f, 22f, 571f, 119f), 16f, 16f, paint(navy, 12f, true))
        canvas.drawRoundRect(android.graphics.RectF(24f, 22f, 32f, 119f), 4f, 4f, paint(gold, 10f))
        canvas.drawText(if (english) "BABY MOVEMENT REPORT" else "बाळाच्या हालचालींचा अहवाल", 48f, 58f, titlePaint)
        canvas.drawText(if (english) titleEn + " · " + monthLabel else titleMr + " · " + monthLabel, 48f, 81f, subPaint)
        canvas.drawText(if (first) (if (english) "Monthly summary" else "मासिक सारांश") else (if (english) "Session details · continued" else "सत्र तपशील · पुढे"), 48f, 101f, subPaint)
        var tableTop = 156f
        if (first) {
            val metrics = listOf(
                Triple(if (english) "SESSIONS" else "सत्रे", sessions.size.toString(), blue),
                Triple(if (english) "MOVEMENTS" else "हालचाली", total.toString(), navy),
                Triple(if (english) "AVG / SESSION" else "सरासरी", "%.1f".format(average), gold)
            )
            metrics.forEachIndexed { i, metric ->
                val left = 36f + i * 177f
                canvas.drawRoundRect(android.graphics.RectF(left, 135f, left + 164f, 203f), 11f, 11f, paint(pale, 10f))
                canvas.drawRoundRect(android.graphics.RectF(left, 135f, left + 5f, 203f), 3f, 3f, paint(metric.third, 10f))
                canvas.drawText(metric.first, left + 15f, 156f, labelPaint)
                canvas.drawText(metric.second, left + 15f, 185f, numberPaint)
            }
            canvas.drawText(if (english) "DAILY MOVEMENT PATTERN" else "दैनिक हालचालींचा आलेख", 36f, 226f, paint(navy, 10f, true))
            canvas.drawRoundRect(android.graphics.RectF(36f, 238f, 559f, 298f), 8f, 8f, paint(pale, 9f))
            val slot = 505f / daily.size.coerceAtLeast(1)
            daily.forEachIndexed { i, value ->
                val barHeight = if (value == 0) 1.5f else (value.toFloat() / maxDaily * 39f).coerceAtLeast(3f)
                val left = 45f + i * slot + slot * .2f
                canvas.drawRoundRect(android.graphics.RectF(left, 286f - barHeight, left + slot * .58f, 286f), 2f, 2f,
                    paint(if (value > 0) blue else android.graphics.Color.rgb(205, 216, 228), 8f))
            }
            canvas.drawText("1", 45f, 293f, smallPaint)
            canvas.drawText(month.lengthOfMonth().toString(), 538f, 293f, smallPaint)
            tableTop = 316f
        } else {
            canvas.drawText(if (english) "Session records" else "सत्र नोंदी", 36f, 143f, paint(navy, 11f, true))
        }
        canvas.drawText(if (english) "SESSION DETAILS" else "सत्रांचा तपशील", 36f, tableTop - 9f, paint(navy, 10f, true))
        y = tableHeader(canvas, tableTop)
    }
    fun finishPage() {
        val canvas = cv ?: return
        canvas.drawLine(36f, 805f, 559f, 805f, paint(android.graphics.Color.rgb(220, 228, 237), 1f))
        canvas.drawText(if (english) "Daily Counter · Personal movement log · Not a medical assessment" else "दैनिक मोजणी · वैयक्तिक नोंद · वैद्यकीय मूल्यांकन नाही", 36f, 823f, footerPaint)
        canvas.drawText(pageNumber.toString(), 544f, 823f, paint(navy, 9f, true))
        pdf.finishPage(currentPage!!)
        currentPage = null
        cv = null
    }
    startPage(true)
    sorted.forEachIndexed { index, session ->
        if (y > 775f) {
            finishPage()
            startPage(false)
        }
        val canvas = cv!!
        if (index % 2 == 0) canvas.drawRoundRect(android.graphics.RectF(36f, y, 559f, y + 25f), 4f, 4f, paint(pale, 8f))
        val dateLabel = runCatching { LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("dd MMM")) }.getOrDefault(session.date)
        val end = session.endedAt ?: minOf(session.startedAt + CounterRepository.TWO_HOURS_MS, System.currentTimeMillis())
        val duration = (end - session.startedAt).coerceAtLeast(0L) / 60000L
        val methodLabel = if (session.trackingMode == "ONE_HOUR") { if (english) "1-hour" else "१ तास" } else { if (english) "To 10" else "१० पर्यंत" }
        val timeLabel = session.timeToTenMs?.let { "%.1f min".format(it / 60000.0) } ?: (duration.toString() + " min")
        val values = listOf(dateLabel, periodLabel(session.dayPeriod, english), sessionTime(session.startedAt),
            methodLabel, session.movementCount.toString(), timeLabel)
        values.forEachIndexed { col, value ->
            if (col == 4) {
                canvas.drawRoundRect(android.graphics.RectF(xs[col] + 5f, y + 4f, xs[col] + 49f, y + 21f), 6f, 6f, paint(android.graphics.Color.rgb(222, 237, 249), 8f))
                canvas.drawText(value, xs[col] + 20f, y + 17f, paint(navy, 10f, true))
            } else {
                canvas.drawText(value.take(if (col == 5) 11 else 14), xs[col] + 7f, y + 16f, bodyPaint)
            }
        }
        y += 27f
    }
    finishPage()
    val file = File(context.cacheDir, "baby-movement-" + month.year + "-" + month.monthValue + ".pdf")
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, if (english) "Share doctor report" else "अहवाल शेअर करा"))
}
